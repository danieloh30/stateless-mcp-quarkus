#!/usr/bin/env python3
"""Probe released candidates in a temporary copy, preserving the Quarkus 3 baseline."""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from urllib.request import urlopen
import xml.etree.ElementTree as ET

CENTRAL = "https://repo.maven.apache.org/maven2/"
MODULES = {"mcp-server": "dev.helios.HeliosMcpTest",
           "agent": "dev.helios.agent.rest.AgentStartupTest"}
STABLE = re.compile(r"^\d+(?:\.\d+){1,3}(?:\.Final)?$")
BLOCKER = re.compile(r"(?:NoClassDefFoundError|ClassNotFoundException)[^\n]*io[./]quarkus[./]jackson[./]ObjectMapperCustomizer")


def version_key(value):
    numbers = tuple(map(int, value.removesuffix(".Final").split(".")))
    return numbers + (0,) * (4 - len(numbers))


def fetch(path):
    with urlopen(CENTRAL + path, timeout=30) as response:
        return response.read().decode()


def releases(path, read):
    return {v.text for v in ET.fromstring(read(path + "/maven-metadata.xml"))
            .findall("./versioning/versions/version") if v.text and STABLE.fullmatch(v.text)}


def resolve_versions(read=fetch):
    # Both platform BOMs and the Maven plugin share a version in this project.
    candidates = releases("io/quarkus/platform/quarkus-bom", read)
    candidates &= releases("io/quarkus/platform/quarkus-langchain4j-bom", read)
    candidates &= releases("io/quarkus/platform/quarkus-maven-plugin", read)
    quarkus = max((v for v in candidates if v.startswith("4.")), key=version_key)
    mcp = max(releases("io/quarkiverse/mcp/quarkus-mcp-server-http", read), key=version_key)
    return {"quarkus": quarkus, "mcp_server": mcp}


def replace_once(text, pattern, value):
    updated, count = re.subn(pattern, lambda m: m[1] + value + m[2], text)
    if count != 1:
        raise ValueError(f"Expected exactly one match for {pattern}; found {count}")
    return updated


def prepare(source, candidate, versions, output):
    if candidate.resolve() == source.resolve() or candidate.exists():
        raise ValueError("Candidate must be a new isolated directory")
    candidate.mkdir()
    # Copy only build inputs, excluding stale targets, credentials and local configuration.
    for name in ("pom.xml", "mvnw", "mvnw.cmd"):
        shutil.copy2(source / name, candidate / name)
    for name in (".mvn", *MODULES):
        shutil.copytree(source / name, candidate / name,
                        ignore=shutil.ignore_patterns("target", ".env", ".env.*"))
    parent = candidate / "pom.xml"
    parent.write_text(replace_once(parent.read_text(),
                      r"(<quarkus\.platform\.version>)[^<]+(</quarkus\.platform\.version>)",
                      versions["quarkus"]))
    server = candidate / "mcp-server/pom.xml"
    server.write_text(replace_once(server.read_text(),
                      r"(<artifactId>quarkus-mcp-server-http</artifactId>\s*<version>)[^<]+(</version>)",
                      versions["mcp_server"]))
    for name in ("pom.xml", "mcp-server/pom.xml", "agent/pom.xml"):
        destination = output / "candidate" / name
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(candidate / name, destination)


def test_evidence(candidate, output):
    evidence = {}
    for module, expected in MODULES.items():
        reports = list((candidate / module / "target/surefire-reports").glob("TEST-*.xml"))
        totals = dict(tests=0, failures=0, errors=0, skipped=0)
        expected_passed = False
        for path in reports:
            suite = ET.parse(path).getroot()
            counts = {key: int(suite.get(key, "0")) for key in totals}
            for key, value in counts.items():
                totals[key] += value
            if suite.get("name") == expected:
                expected_passed = counts["tests"] > 0 and not any(counts[k] for k in ("failures", "errors", "skipped"))
            destination = output / module / "surefire-reports"
            destination.mkdir(parents=True, exist_ok=True)
            shutil.copy2(path, destination / path.name)
        evidence[module] = {**totals, "expected_suite_passed": expected_passed}
    return evidence


def classify(code, log, evidence):
    if code == 0:
        return "compatible" if all(
            evidence.get(module, {}).get("expected_suite_passed")
            and evidence[module]["tests"] > 0
            and not any(evidence[module][k] for k in ("failures", "errors", "skipped"))
            for module in MODULES) else "error"
    if code == 124 or any(term in log for term in (
            "Could not transfer artifact", "Could not resolve dependencies", "Could not resolve host",
            "Could not find a valid Docker environment", "Could not connect to Ryuk")):
        return "error"
    return "blocked" if BLOCKER.search(log) else "regression"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--allow-known-blocker", action="store_true")
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    source = Path(__file__).resolve().parents[1]
    report = {"status": "error", "checked_at": datetime.now(timezone.utc).isoformat(),
              "commit": os.environ.get("GITHUB_SHA", "local working tree")}
    try:
        report["versions"] = resolve_versions()
        with tempfile.TemporaryDirectory(prefix="quarkus4-readiness-") as directory:
            candidate = Path(directory) / "candidate"
            prepare(source, candidate, report["versions"], output)
            environment = dict(os.environ, OPENAI_API_KEY="readiness-test-key",
                               QUARKUS_LANGCHAIN4J_OPENAI_BASE_URL="http://127.0.0.1:1/v1")
            with (output / "maven.log").open("w") as log:
                try:
                    result = subprocess.run(["./mvnw", "-B", "-ntp", "-U", "clean", "verify"],
                                            cwd=candidate, env=environment, stdout=log,
                                            stderr=subprocess.STDOUT, timeout=1800, check=False)
                    code = result.returncode
                except subprocess.TimeoutExpired:
                    code = 124
                    log.write("\nReadiness probe timed out\n")
            report["maven_exit_code"] = code
            report["modules"] = test_evidence(candidate, output)
            report["status"] = classify(code, (output / "maven.log").read_text(), report["modules"])
    except Exception as failure:
        report["error"] = f"{type(failure).__name__}: {failure}"
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
    summary = (f"## Quarkus 4 MCP compatibility: {report['status'].upper()}\n\n"
               "Both module suites and packaging must pass before reviewing the Dependabot restriction. "
               "This probe changes only a temporary copy. Application migration may still be required; "
               "no dependencies are updated or merged automatically.\n\n"
               f"```json\n{json.dumps(report, indent=2)}\n```\n\n"
               "Download the artifact for candidate POMs, Maven logs and JUnit reports.\n")
    (output / "summary.md").write_text(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as target:
            target.write(summary)
    print(summary)
    return 0 if report["status"] == "compatible" or (
        args.allow_known_blocker and report["status"] == "blocked") else 1


if __name__ == "__main__":
    raise SystemExit(main())
