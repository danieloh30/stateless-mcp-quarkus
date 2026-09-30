#!/usr/bin/env python3
"""Exercise deployment ordering and failures without modifying a cluster."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
DIGEST = "image-registry.example/helios/app@sha256:" + "a" * 64
MOCK_OC = '''#!/usr/bin/env python3
import json, os, sys
args = sys.argv[1:]
event = {"args": args}
if args[:2] == ["apply", "-f"] and args[2] == "-":
    event["manifest"] = json.load(sys.stdin)
with open(os.environ["DEPLOY_TEST_LOG"], "a") as log:
    log.write(json.dumps(event) + "\\n")
failure = os.environ.get("DEPLOY_TEST_FAILURE", "")
if args[0] == "start-build" and failure == "build":
    sys.exit(1)
if args[:3] == ["rollout", "status", "deploy/stateless-mcp-quarkus"] and failure == "rollout":
    sys.exit(1)
if args[0] == "project":
    print("helios")
elif args[:2] == ["get", "istag"]:
    image = "" if failure == "image" else os.environ["DEPLOY_TEST_IMAGE"]
    print(json.dumps({"image": {"dockerImageReference": image}}))
elif args[:2] == ["get", "route"]:
    print("helios.example")
elif args[0] == "create":
    print('{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"test"}}')
'''


class DeploymentTest(unittest.TestCase):
    def run_deployment(self, failure="", mode="jvm"):
        with tempfile.TemporaryDirectory() as directory:
            workspace = Path(directory)
            shutil.copy(ROOT / "deploy-openshift.sh", workspace)
            for module, app in (("mcp-server", "stateless-mcp-quarkus"),
                                ("agent", "stateless-agent")):
                target = workspace / module / "target" / "kubernetes"
                target.mkdir(parents=True)
                # A different version ensures the script uses BuildConfig output.
                resources = [
                    {"kind": "BuildConfig", "metadata": {"name": app},
                     "spec": {"output": {"to": {"name": app + ":2.0.0"}}}},
                    {"kind": "ImageStream", "metadata": {"name": app}},
                    {"kind": "Deployment", "metadata": {"name": app},
                     "spec": {"template": {"spec": {"containers": [
                         {"name": app, "image": app + ":2.0.0"},
                         {"name": "sidecar", "image": "sidecar:1"}]}}}},
                ]
                (target / "openshift.json").write_text(json.dumps(resources))
            binary = workspace / "bin"
            binary.mkdir()
            (binary / "oc").write_text(MOCK_OC)
            (binary / "oc").chmod(0o755)
            # Packaging is outside the scope of these deployment control tests.
            (workspace / "mvnw").write_text("#!/bin/sh\nexit 0\n")
            (workspace / "mvnw").chmod(0o755)
            log = workspace / "events.jsonl"
            env = dict(os.environ, PATH=str(binary) + os.pathsep + os.environ["PATH"],
                       OPENAI_API_KEY="", DEPLOY_TEST_LOG=str(log),
                       DEPLOY_TEST_IMAGE=DIGEST, DEPLOY_TEST_FAILURE=failure)
            result = subprocess.run(["bash", "deploy-openshift.sh", mode],
                                    cwd=workspace, env=env, capture_output=True, text=True)
            events = [json.loads(line) for line in log.read_text().splitlines()]
            return result, events

    @staticmethod
    def deployments(events):
        return [(index, item) for index, event in enumerate(events)
                for item in event.get("manifest", {}).get("items", [])
                if item["kind"] == "Deployment"]

    def test_images_built_before_deployments(self):
        result, events = self.run_deployment()
        self.assertEqual(result.returncode, 0, result.stderr)
        deployments = self.deployments(events)
        self.assertEqual(len(deployments), 2)
        for index, deployment in deployments:
            app = deployment["metadata"]["name"]
            earlier = [event["args"] for event in events[:index]]
            self.assertTrue(any(args[:2] == ["start-build", app] for args in earlier))
            self.assertIn(["get", "istag", app + ":2.0.0", "-o", "json"], earlier)
            containers = deployment["spec"]["template"]["spec"]["containers"]
            self.assertEqual(containers[0]["image"], DIGEST)
            self.assertEqual(containers[1]["image"], "sidecar:1")
        mcp_ready = next(i for i, event in enumerate(events)
                         if event["args"][:3] == ["rollout", "status", "deploy/stateless-mcp-quarkus"])
        agent_build = next(i for i, event in enumerate(events)
                           if event["args"][:2] == ["start-build", "stateless-agent"])
        self.assertLess(mcp_ready, agent_build)

    def test_failed_build_or_missing_digest_never_deploys(self):
        for failure in ("build", "image"):
            with self.subTest(failure=failure):
                result, events = self.run_deployment(failure)
                self.assertNotEqual(result.returncode, 0)
                self.assertEqual(self.deployments(events), [])
                self.assertNotIn("Helios Control Tower", result.stdout)

    def test_failed_rollout_stops_before_agent(self):
        result, events = self.run_deployment("rollout")
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(len(self.deployments(events)), 1)
        self.assertFalse(any(event["args"][:2] == ["start-build", "stateless-agent"]
                             for event in events))
        self.assertNotIn("Helios Control Tower", result.stdout)

    def test_native_rollout_failure_is_not_ignored(self):
        result, events = self.run_deployment("rollout", "native")
        self.assertNotEqual(result.returncode, 0)
        self.assertFalse(any(event["args"][0] == "start-build" for event in events))
        self.assertNotIn("Helios Control Tower", result.stdout)


if __name__ == "__main__":
    unittest.main()
