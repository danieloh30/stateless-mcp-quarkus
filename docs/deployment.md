# Deployment

Move from a fixed OpenShift fleet to Knative scale-to-zero while keeping the agent endpoint stable.

## 1. Select a project

```bash
oc login ...
oc new-project helios
```

Cluster-admin access is needed only when installing the cluster-scoped Serverless operator.

## 2. Install OpenShift Serverless

```bash
./install-serverless.sh
```

The script applies the operator and Knative Serving resources, then waits for both to become ready.

## 3. Deploy the platform

```bash
export OPENAI_API_KEY=sk-...
./deploy-openshift.sh
```

Use `./deploy-openshift.sh native` for a native build in a compatible build environment. The API key is stored in `Secret/helios-openai`; it is never baked into an image.

## 4. Prove horizontal scaling

```bash
oc scale deploy/stateless-mcp-quarkus --replicas=8
```

The console distinguishes ready pods from the replicas observed serving requests.

![OpenShift topology in fixed-fleet mode](images/openshift-normal-pods.png)

## 5. Switch to scale-to-zero

```bash
./knative-mode.sh enable
oc get pod -l serving.knative.dev/service=stateless-mcp-knative -w
```

After the idle window, MCP pod count reaches zero. Passive readiness and instance polling do not wake the service. Invoke a tool in the console to cold-start it again.

![OpenShift topology with the Knative Service](images/openshift-knative-service.png)

Return to the regular fleet and restore its previous replica count:

```bash
./knative-mode.sh disable
```

!!! tip "Conference demo sequence"
    Dev mode → five-replica local cluster → OpenShift fleet → Knative scale-to-zero.
