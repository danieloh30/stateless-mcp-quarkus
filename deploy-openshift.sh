#!/usr/bin/env bash
# Deploy the full topology to OpenShift:
#   agent (Route) → nginx L7 proxy → 5 stateless MCP replicas → shared PostgreSQL
# The proxy and MCP servers are internal; only the agent is exposed via a Route.
#
#   ./deploy-openshift.sh          # JVM images (portable default)
#   ./deploy-openshift.sh native   # native images (requires a matching native-build environment)
#
# Prereqs: `oc login ...`, a selected project (`oc new-project helios`), and
# OPENAI_API_KEY exported. This script creates/refreshes the helios-openai
# Secret; the generated Agent Deployment imports it through envFrom.
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
command -v jq >/dev/null 2>&1 || { echo "ERROR: 'jq' CLI not found." >&2; exit 1; }
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
    local manifest="$module/target/kubernetes/openshift.json"
    # Create build prerequisites first. A Deployment created before its image
    # exists enters ImagePullBackOff; on a redeploy it can also use an old tag.
    jq '{apiVersion: "v1", kind: "List", items: map(select(.kind != "Deployment"))}' \
      "$manifest" | oc apply -f -
    oc start-build "$app_name" --from-dir="$module/target/quarkus-app" --follow --wait
    local image_tag image_ref
    image_tag="$(jq -er --arg app "$app_name" \
      '.[] | select(.kind == "BuildConfig" and .metadata.name == $app) | .spec.output.to.name' \
      "$manifest")"
    image_ref="$(oc get istag "$image_tag" -o json | jq -er \
      '.image.dockerImageReference | select(test("@sha256:[0-9a-f]{64}$"))')"
    # Apply the Deployment once, already pinned to the image that just built.
    jq --arg app "$app_name" --arg image "$image_ref" \
      '{apiVersion: "v1", kind: "List", items: [
        .[] | select(.kind == "Deployment") |
        (.spec.template.spec.containers[] | select(.name == $app) | .image) = $image
      ]}' "$manifest" | oc apply -f -
  else
    ./mvnw -q -pl "$module" clean package -DskipTests -Dnative \
      -Dquarkus.native.container-build=true -Dquarkus.openshift.deploy=true \
      -Dquarkus.openshift.build-timeout="$OPENSHIFT_BUILD_TIMEOUT"
  fi
  # Stop on an unhealthy module before deploying tiers that depend on it.
  oc rollout status deploy/"$app_name" --timeout=300s
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
echo "==> Agent configured with OPENAI_API_KEY from Secret/helios-openai"

ROUTE="$(oc get route stateless-agent -o jsonpath='{.spec.host}' 2>/dev/null || true)"
echo
[ -n "$ROUTE" ] && echo "==> Helios Control Tower (agent):  https://$ROUTE/" \
                || echo "==> Deployed. Find the route:  oc get route stateless-agent"
echo
echo "Scale the stateless MCP fleet:   oc scale deploy/stateless-mcp-quarkus --replicas=8"
echo "Scale-to-zero (Knative):         ./install-serverless.sh  then  ./knative-mode.sh enable"
