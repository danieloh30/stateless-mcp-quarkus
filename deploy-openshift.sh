#!/usr/bin/env bash
# Deploy the stateless MCP server to OpenShift as a Quarkus NATIVE image, with a
# shared PostgreSQL. Usage:
#   ./deploy-openshift.sh          # native image (instant start, low memory)
#   ./deploy-openshift.sh jvm      # JVM image (faster build, for quick demos)
#
# Prereqs: logged in with `oc login`, and a current project selected
# (`oc new-project helios` or `oc project <name>`).
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
echo "==> Waiting for PostgreSQL to be ready"
oc rollout status deploy/helios-postgres --timeout=180s

echo "==> Building and deploying the app ($MODE) via quarkus-openshift"
if [ "$MODE" = "jvm" ]; then
  mvn -q clean package -DskipTests \
    -Dquarkus.openshift.deploy=true
else
  mvn -q clean package -DskipTests \
    -Dnative -Dquarkus.native.container-build=true \
    -Dquarkus.openshift.deploy=true
fi

echo "==> Waiting for the app rollout"
oc rollout status deploy/stateless-mcp-quarkus --timeout=300s || true

ROUTE="$(oc get route stateless-mcp-quarkus -o jsonpath='{.spec.host}' 2>/dev/null || true)"
echo
if [ -n "$ROUTE" ]; then
  echo "==> Helios Control Tower:  https://$ROUTE/"
  echo "    MCP endpoint:          https://$ROUTE/mcp"
else
  echo "==> Deployed. Find the route with:  oc get route stateless-mcp-quarkus"
fi
echo
echo "Scale the stateless replicas:   oc scale deploy/stateless-mcp-quarkus --replicas=5"
echo "For scale-to-zero (Knative):    oc apply -f k8s/knative-service.yaml"
