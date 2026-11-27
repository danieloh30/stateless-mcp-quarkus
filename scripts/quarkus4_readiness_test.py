"""Safeguards for the scheduled candidate probe; no network or Java required."""
from pathlib import Path
import tempfile
import unittest

import quarkus4_readiness as monitor


def metadata(*versions):
    return "<metadata><versioning><versions>" + "".join(
        f"<version>{version}</version>" for version in versions) + "</versions></versioning></metadata>"


class ReadinessTest(unittest.TestCase):
    def test_selects_newest_stable_stack_with_all_platform_artifacts(self):
        def read(path):
            if "quarkiverse/mcp" in path:
                return metadata("2.9.0", "2.10.0", "3.0.0.CR1")
            if "langchain4j" in path:
                return metadata("3.40.1.2", "4.0.1", "4.0.2")
            return metadata("3.40.1.2", "4.0.1", "4.0.2", "4.1.0.CR1", "4.0.3")
        self.assertEqual(monitor.resolve_versions(read), {"quarkus": "4.0.2", "mcp_server": "2.10.0"})

    def test_missing_quarkus4_release_fails(self):
        with self.assertRaises(ValueError):
            monitor.resolve_versions(lambda _: metadata("3.40.1.2"))

    def test_candidate_preserves_sources_and_excludes_stale_reports(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            (source / "pom.xml").write_text("<quarkus.platform.version>3.40.1.2</quarkus.platform.version>")
            for name in ("mvnw", "mvnw.cmd"):
                (source / name).touch()
            (source / ".mvn").mkdir()
            for module in monitor.MODULES:
                (source / module / "target/surefire-reports").mkdir(parents=True)
                (source / module / "target/surefire-reports/TEST-old.xml").write_text("stale")
                (source / module / ".env").write_text("secret")
                (source / module / "pom.xml").write_text(
                    "<artifactId>quarkus-mcp-server-http</artifactId><version>2.1.0</version>")
            output = root / "report"
            output.mkdir()
            candidate = root / "candidate"
            monitor.prepare(source, candidate, {"quarkus": "4.0.1", "mcp_server": "2.2.0"}, output)
            self.assertIn("3.40.1.2", (source / "pom.xml").read_text())
            self.assertIn("4.0.1", (candidate / "pom.xml").read_text())
            self.assertIn("2.2.0", (candidate / "mcp-server/pom.xml").read_text())
            self.assertFalse((candidate / "mcp-server/target").exists())
            self.assertFalse((candidate / "agent/.env").exists())
            with self.assertRaises(ValueError):
                monitor.prepare(source, source, {}, output)
            # A green exit without fresh reports cannot establish compatibility.
            evidence = monitor.test_evidence(candidate, output)
            self.assertEqual(monitor.classify(0, "", evidence), "error")

    def test_requires_both_expected_suites_and_no_skipped_tests(self):
        good = dict(tests=2, failures=0, errors=0, skipped=0, expected_suite_passed=True)
        evidence = {module: dict(good) for module in monitor.MODULES}
        self.assertEqual(monitor.classify(0, "", evidence), "compatible")
        evidence["agent"]["expected_suite_passed"] = False
        self.assertEqual(monitor.classify(0, "", evidence), "error")
        evidence["agent"] = dict(good, skipped=1)
        self.assertEqual(monitor.classify(0, "", evidence), "error")

    def test_distinguishes_upstream_blocker_from_other_failures(self):
        blocker = "NoClassDefFoundError: io/quarkus/jackson/ObjectMapperCustomizer"
        self.assertEqual(monitor.classify(1, blocker, {}), "blocked")
        self.assertEqual(monitor.classify(1, "compilation failed", {}), "regression")
        self.assertEqual(monitor.classify(1, blocker + " Could not transfer artifact", {}), "error")
        self.assertEqual(monitor.classify(124, blocker, {}), "error")


if __name__ == "__main__":
    unittest.main()
