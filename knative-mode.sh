#!/usr/bin/env bash
# Switch the deployed demo between the regular L7-balanced fleet and a
# cluster-local Knative Service that scales the MCP server to zero.
set -euo pipefail
cd "$(dirname "$0")"

MODE="${1:-enable}"
command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' CLI not found." >&2; exit 1; }
oc whoami >/dev/null 2>&1 || { echo "ERROR: not logged in. Run 'oc login ...' first." >&2; exit 1; }

case "$MODE" in
  enable)
    serving_ready="$(oc get knativeserving/knative-serving -n knative-serving \
      -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true)"
    [ "$serving_ready" = "True" ] \
      || { echo "ERROR: Knative Serving is not ready. Run ./install-serverless.sh first." >&2; exit 1; }
    image_ref="$(oc get istag/stateless-mcp-quarkus:1.0.0 \
      -o jsonpath='{.image.dockerImageReference}' 2>/dev/null || true)"
    [ -n "$image_ref" ] \
      || { echo "ERROR: MCP image not found. Run ./deploy-openshift.sh first." >&2; exit 1; }

    echo "==> Deploying the cluster-local Knative MCP Service"
    oc apply -f k8s/knative-service.yaml
    # Replace the example namespace/tag with the immutable image digest from
    # whichever OpenShift project is currently selected.
    oc patch ksvc/stateless-mcp-knative --type=json \
      -p="[{\"op\":\"replace\",\"path\":\"/spec/template/spec/containers/0/image\",\"value\":\"${image_ref}\"}]"
    oc wait --for=condition=Ready ksvc/stateless-mcp-knative --timeout=300s

    project="$(oc project -q)"
    replicas="$(oc get deployment/stateless-mcp-quarkus -o jsonpath='{.spec.replicas}')"
    if [ "$replicas" != "0" ]; then
      oc annotate deployment/stateless-mcp-quarkus \
        helios.dev/replicas-before-knative="$replicas" --overwrite
    fi

    echo "==> Pointing the Agent at Knative"
    oc set env deployment/stateless-agent \
      MCP_FLEET_URL="http://stateless-mcp-knative.${project}.svc.cluster.local/mcp" \
      MCP_FLEET_DISCOVERY_HOST=stateless-mcp-knative-headless \
      MCP_FLEET_SCALE_TO_ZERO=true \
      MCP_FLEET_AUTO_HEALTH_CHECK=false
    # The normal readiness check includes MCP server/discover. Running it every
    # ten seconds would be real traffic and intentionally keep Knative warm.
    # In this mode probe only the Agent; the UI reports downstream readiness.
    oc patch deployment/stateless-agent --type=json \
      -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/httpGet/path","value":"/q/health/live"}]'
    oc rollout status deployment/stateless-agent --timeout=300s

    echo "==> Scaling down the regular fleet and its L7 proxy"
    oc scale deployment/stateless-mcp-quarkus --replicas=0
    oc scale deployment/stateless-mcp-l7 --replicas=0
    echo "==> Knative mode enabled. Watch: oc get pod -l serving.knative.dev/service=stateless-mcp-knative -w"
    ;;
  disable)
    replicas="$(oc get deployment/stateless-mcp-quarkus \
      -o jsonpath='{.metadata.annotations.helios\.dev/replicas-before-knative}' 2>/dev/null || true)"
    replicas="${replicas:-5}"
    echo "==> Restoring the regular MCP fleet (${replicas} replicas)"
    oc scale deployment/stateless-mcp-quarkus --replicas="$replicas"
    oc scale deployment/stateless-mcp-l7 --replicas=1
    oc rollout status deployment/stateless-mcp-quarkus --timeout=300s
    oc rollout status deployment/stateless-mcp-l7 --timeout=180s

    oc set env deployment/stateless-agent \
      MCP_FLEET_URL- MCP_FLEET_DISCOVERY_HOST- MCP_FLEET_SCALE_TO_ZERO- \
      MCP_FLEET_AUTO_HEALTH_CHECK-
    oc patch deployment/stateless-agent --type=json \
      -p='[{"op":"replace","path":"/spec/template/spec/containers/0/readinessProbe/httpGet/path","value":"/q/health/ready"}]'
    oc rollout status deployment/stateless-agent --timeout=300s
    oc delete -f k8s/knative-service.yaml --ignore-not-found
    echo "==> Regular fleet mode restored"
    ;;
  *)
    echo "Usage: $0 [enable|disable]" >&2
    exit 1
    ;;
esac
