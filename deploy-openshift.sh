#!/usr/bin/env bash
# Deploy the full topology to OpenShift:
#   agent (Route) → nginx L7 proxy → 5 stateless MCP replicas → shared PostgreSQL
# The proxy and MCP servers are internal; only the agent is exposed via a Route.
#
#   ./deploy-openshift.sh          # JVM images (portable default)
#   ./deploy-openshift.sh native   # native images (requires a matching native-build environment)
#
# Prereqs: `oc login ...` and a selected project (`oc new-project helios`).
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-jvm}"

# Native OpenShift builds can need more than Quarkus's default five-minute limit.
OPENSHIFT_BUILD_TIMEOUT="${OPENSHIFT_BUILD_TIMEOUT:-15M}"
export KUBERNETES_CONNECTION_TIMEOUT="${KUBERNETES_CONNECTION_TIMEOUT:-30000}"
export KUBERNETES_REQUEST_TIMEOUT="${KUBERNETES_REQUEST_TIMEOUT:-120000}"

if [ "$MODE" != "jvm" ] && [ "$MODE" != "native" ]; then
  echo "ERROR: mode must be 'jvm' or 'native'." >&2
  exit 1
fi

command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' CLI not found." >&2; exit 1; }
oc whoami >/dev/null 2>&1 || { echo "ERROR: not logged in. Run 'oc login ...' first." >&2; exit 1; }
echo "==> Project: $(oc project -q)"

if [ -n "${OPENAI_API_KEY:-}" ]; then
  echo "==> Creating/refreshing the Agent API-key Secret"
  oc create secret generic helios-openai --from-literal=OPENAI_API_KEY="$OPENAI_API_KEY" \
    --dry-run=client -o yaml | oc apply -f -
elif ! oc get secret helios-openai >/dev/null 2>&1; then
  echo "ERROR: export OPENAI_API_KEY or create the helios-openai Secret first." >&2
  exit 1
else
  echo "==> Reusing existing helios-openai Secret"
fi

echo "==> Creating/refreshing the DB init ConfigMap from compose/init.sql"
oc create configmap helios-initdb --from-file=init.sql=compose/init.sql \
  --dry-run=client -o yaml | oc apply -f -

echo "==> Applying shared PostgreSQL (Secret + Deployment + Service)"
oc apply -f k8s/postgres.yaml
oc rollout status deploy/helios-postgres --timeout=180s

echo "==> Building and deploying both apps ($MODE) via quarkus-openshift"
deploy_module() {
  local module="$1"
  local app_name="$2"
  echo "==> Deploying $module"
  if [ "$MODE" = "jvm" ]; then
    # Generate the manifests locally, then let oc stream the packaged app. This
    # avoids Fabric8's slow instantiatebinary upload path on remote clusters.
    ./mvnw -q -pl "$module" clean package -DskipTests
    oc apply -f "$module/target/kubernetes/openshift.yml"
    oc start-build "$app_name" --from-dir="$module/target/quarkus-app" --follow --wait
    # The manifest is applied before the build and may resolve the mutable 1.0.0
    # tag to its previous digest. Pin the Deployment to the digest just built.
    local image_ref
    image_ref="$(oc get istag "$app_name:1.0.0" -o jsonpath='{.image.dockerImageReference}')"
    oc set image deployment/"$app_name" "$app_name=$image_ref"
  else
    ./mvnw -q -pl "$module" clean package -DskipTests -Dnative \
      -Dquarkus.native.container-build=true -Dquarkus.openshift.deploy=true \
      -Dquarkus.openshift.build-timeout="$OPENSHIFT_BUILD_TIMEOUT"
  fi
}

if [ "$MODE" = "native" ]; then
  echo "==> Native mode requires the builder architecture to match the OpenShift worker architecture"
fi
deploy_module mcp-server stateless-mcp-quarkus
echo "==> Applying the internal L7 proxy and ready-replica discovery Service"
oc apply -f k8s/mcp-l7-proxy.yaml
oc rollout restart deploy/stateless-mcp-l7
oc rollout status deploy/stateless-mcp-l7 --timeout=180s
deploy_module agent stateless-agent

echo "==> Waiting for rollouts"
oc rollout status deploy/stateless-mcp-quarkus --timeout=300s || true
oc rollout status deploy/stateless-agent --timeout=300s || true

ROUTE="$(oc get route stateless-agent -o jsonpath='{.spec.host}' 2>/dev/null || true)"
echo
[ -n "$ROUTE" ] && echo "==> Helios Control Tower (agent):  https://$ROUTE/" \
                || echo "==> Deployed. Find the route:  oc get route stateless-agent"
echo
echo "Scale the stateless MCP fleet:   oc scale deploy/stateless-mcp-quarkus --replicas=8"
echo "Scale-to-zero (Knative):         ./install-serverless.sh  then  oc apply -f k8s/knative-service.yaml"
