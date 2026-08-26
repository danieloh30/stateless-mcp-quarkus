#!/usr/bin/env bash
# Install OpenShift Serverless (Knative) and enable Knative Serving, so the
# scale-to-zero demo (k8s/knative-service.yaml) can run. Idempotent.
#
#   ./install-serverless.sh
#
# Prereqs: cluster-admin `oc login` (installing an operator is cluster-scoped).
set -euo pipefail
cd "$(dirname "$0")"

command -v oc >/dev/null 2>&1 || { echo "ERROR: 'oc' CLI not found." >&2; exit 1; }
oc whoami >/dev/null 2>&1 || { echo "ERROR: not logged in. Run 'oc login ...' first." >&2; exit 1; }

echo "==> Installing the OpenShift Serverless operator (OLM Subscription)"
oc apply -f k8s/serverless-operator.yaml

echo "==> Waiting for the operator to register its CRDs (this can take a few minutes)"
for i in $(seq 1 60); do
  if oc get crd knativeservings.operator.knative.dev >/dev/null 2>&1; then break; fi
  sleep 10
done
oc get crd knativeservings.operator.knative.dev >/dev/null 2>&1 \
  || { echo "ERROR: Serverless operator CRDs not available yet; check the Subscription in openshift-serverless." >&2; exit 1; }

echo "==> Enabling Knative Serving"
oc apply -f k8s/knative-serving.yaml

echo "==> Waiting for Knative Serving to become Ready"
oc wait --for=condition=Ready knativeserving/knative-serving -n knative-serving --timeout=600s

echo "==> OpenShift Serverless is ready. You can now deploy the scale-to-zero MCP fleet:"
echo "    oc apply -f k8s/postgres.yaml"
echo "    oc apply -f k8s/knative-service.yaml"
