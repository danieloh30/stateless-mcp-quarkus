#!/usr/bin/env bash
# Deploy the full topology to OpenShift:
#   agent (Route) → Service → 5 stateless MCP server replicas → shared PostgreSQL
# The MCP servers are internal (ClusterIP Service does the round-robin); only the
# agent is exposed via a Route.
#
#   ./deploy-openshift.sh          # native images (instant start, low memory)
#   ./deploy-openshift.sh jvm      # JVM images (faster build, for quick demos)
#
# Prereqs: `oc login ...` and a selected project (`oc new-project helios`).
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-native}"

command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' CLI not found." >&2; exit 1; }
oc whoami >/dev/null 2>&1 || { echo "ERROR: not logged in. Run 'oc login ...' first." >&2; exit 1; }
echo "==> Project: $(oc project -q)"

echo "==> Creating/refreshing the DB init ConfigMap from compose/init.sql"
oc create configmap helios-initdb --from-file=init.sql=compose/init.sql \
  --dry-run=client -o yaml | oc apply -f -

echo "==> Applying shared PostgreSQL (Secret + Deployment + Service)"
oc apply -f k8s/postgres.yaml
oc rollout status deploy/helios-postgres --timeout=180s

echo "==> Building and deploying both apps ($MODE) via quarkus-openshift"
if [ "$MODE" = "jvm" ]; then
  ./mvnw -q clean package -DskipTests -Dquarkus.openshift.deploy=true
else
  ./mvnw -q clean package -DskipTests -Dnative -Dquarkus.native.container-build=true \
    -Dquarkus.openshift.deploy=true
fi

echo "==> Waiting for rollouts"
oc rollout status deploy/stateless-mcp-server --timeout=300s || true
oc rollout status deploy/stateless-agent --timeout=300s || true

ROUTE="$(oc get route stateless-agent -o jsonpath='{.spec.host}' 2>/dev/null || true)"
echo
[ -n "$ROUTE" ] && echo "==> Helios Control Tower (agent):  https://$ROUTE/" \
                || echo "==> Deployed. Find the route:  oc get route stateless-agent"
echo
echo "Scale the stateless MCP fleet:   oc scale deploy/stateless-mcp-server --replicas=8"
echo "Scale-to-zero (Knative):         ./install-serverless.sh  then  oc apply -f k8s/knative-service.yaml"
