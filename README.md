# Helios Control Tower — Stateless MCP Server on Quarkus

A production-shaped demo of a **stateless, cloud-native MCP (Model Context Protocol) server**
built with **Quarkus** and **Java 25**, with a polished single-page console UI.

> Companion demo for the talk **“Going Stateless: Scaling MCP Servers to Cloud-Native Java & HTTP”**
> (MCP Dev Summit, Toronto 2026).

---

## The enterprise scenario

**Helios Logistics** is a fictional global freight & supply-chain company. Its AI agents and
operations teams need to query live enterprise data — shipment tracking, warehouse inventory,
lane pricing, carrier SLAs, and open exceptions.

Rather than one stateful, session-heavy tool server, Helios runs a **fleet of tiny, single-purpose
MCP tool servers**. Each tool is a stateless lookup: validate arguments → run one query →
return a structured record. Because no state is carried between calls:

- 🔀 **Load balancing is trivial** — any replica can serve any request, no session affinity.
- 📉 **Scale to zero** — when the tools are idle, the platform scales replicas to zero and pays nothing.
- ⚡ **Instant, dense, cheap** — Quarkus starts in milliseconds with a tiny heap, so you can pack many
  specialized tool servers per node (and go GraalVM native / AOT for even more).

The console UI makes statelessness visible: a header badge polls the `getServerInstance` MCP tool
to show which replica is serving right now, and a **“Run 5×”** button fires independent MCP calls —
in the cluster you watch the serving instance rotate while the result never changes.

---

## Architecture

```
  AI Agent (Goose / Claude)            Browser Console (SPA)
          │  MCP / JSON-RPC                     │  REST
          ▼  (Streamable HTTP, stateless)       ▼
   ┌───────────────────────────────────────────────────────┐
   │            Load balancer (nginx / K8s Service)         │  :8080
   └───────────────────────────────────────────────────────┘
        │        │        │        │        │
        ▼        ▼        ▼        ▼        ▼    (scale to zero when idle)
   ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
   │ app1 │ │ app2 │ │ app3 │ │ app4 │ │ app5 │   5 identical STATELESS
   │Quarkus│ │Quarkus│ │Quarkus│ │Quarkus│ │Quarkus│  MCP replicas
   └──────┘ └──────┘ └──────┘ └──────┘ └──────┘
        └────────┴────────┼────────┴────────┘
                          ▼
                 ┌──────────────────┐
                 │   PostgreSQL     │   the ONLY place state lives —
                 │   (shared)       │   shared by every replica
                 └──────────────────┘
```

The replicas hold **zero** in-JVM state; all data lives in a shared PostgreSQL. That is what lets
requests round-robin freely across replicas — any instance serves any request, and the fleet can
scale to zero when idle. Everything is exposed as `@Tool` methods over MCP, and the browser console
is itself a **pure MCP client** — it calls `tools/list` and `tools/call` straight against `/mcp`,
the same protocol an AI agent uses. The server runs in **stateless mode**
(`quarkus.mcp.server.http.streamable.auto-init=true`): no `initialize` handshake or session is
required, so each request is independent and load-balances across replicas.

---

## MCP tools

| Tool | What it does | Example args |
|------|--------------|--------------|
| `getShipmentStatus` | Live status, carrier, route, ETA for a shipment | `HLX-10032291` |
| `getWarehouseInventory` | On-hand / reserved / available stock for a SKU | `SKU-COLD-4521` |
| `estimateDelivery` | Transit days & cost for a lane between two hubs | `FRA → YYZ, 620 kg` |
| `getCarrierSla` | On-time %, transit days, damage rate for a carrier | `HELIOS-AIR` |
| `listOpenExceptions` | Shipments needing operator attention in a region | `APAC` |

Every argument is hardened with Jakarta Bean Validation (`@Pattern`, `@Positive`, `@Size`) —
malformed input is rejected before any lookup runs.

---

## Run it — single instance (dev)

Prerequisites: **Java 25+**, **Maven 3.9+**, and a container runtime (**Docker** or **Podman**)
for the database.

```bash
mvn quarkus:dev
```

Quarkus **Dev Services** automatically starts a throwaway **PostgreSQL** container and seeds it
from `import.sql` — no database to install or configure. Then open:

- 🖥️  **Console UI** — <http://localhost:8080/>
- 🔌 **MCP endpoint** — `http://localhost:8080/mcp` (Streamable HTTP)
- ❤️  **Health** — <http://localhost:8080/q/health>

In dev mode there is one replica, so the console shows a single serving instance — same stateless
guarantee, one server.

## Run it — the cluster (see real statelessness)

To actually watch requests spread across a fleet, launch **5 identical stateless replicas** behind
an nginx load balancer, all sharing one PostgreSQL:

```bash
./start-cluster.sh      # builds the app + image, starts Postgres + 5 replicas + LB
open http://localhost:8080
# Pick a tool → "Run 5×" → the serving instance rotates across all 5 replicas,
# while the result never changes.
./stop-cluster.sh       # tear everything down
```

This is the whole point of the talk, made real: no session affinity, plain round-robin, identical
results from any replica.

## Package & native

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar

# GraalVM native executable — instant startup, minimal memory, ideal for scale-to-zero:
mvn clean package -Dnative
```

## Deploy to OpenShift / Kubernetes (native + scale-to-zero)

This is where the talk's thesis pays off: a stateless server is just a cloud-native microservice,
so it deploys, scales, and scales-to-zero like any other. The `quarkus-openshift` extension
generates the manifests (Deployment, Service, Route, S2I BuildConfig, health probes) at build time.

```bash
oc login ... && oc new-project helios

# Build a NATIVE image and deploy it, plus a shared PostgreSQL:
./deploy-openshift.sh            # or: ./deploy-openshift.sh jvm  (faster build)

# Scale the stateless fleet up/down at will — no session affinity to worry about:
oc scale deploy/stateless-mcp-quarkus --replicas=5
```

The datasource comes from the `helios-db` Secret (no secrets in the image); liveness/readiness
probes are wired from `quarkus-smallrye-health`. Generated manifests land in `target/kubernetes/`.

**Scale to zero with Knative** (OpenShift Serverless) — the ultimate stateless payoff. A native
image cold-starts in tens of milliseconds, so running zero replicas when idle is practical:

```bash
oc apply -f k8s/postgres.yaml
oc apply -f k8s/knative-service.yaml    # minScale: 0 — no traffic, no pods, no cost
```

> The demo flow for the talk: **dev (Dev Services) → local cluster (`./start-cluster.sh`, 5 replicas
> round-robin) → OpenShift native → Knative scale-to-zero.**

### Connect an AI agent

Point any MCP client at the Streamable HTTP endpoint. Example Goose extension config:

```yaml
extensions:
  helios:
    type: streamable_http
    uri: http://localhost:8080/mcp
```

---

## Why stateless matters (the talk in one paragraph)

Classic MCP deployments lean on long-lived sessions, which pin a client to one server instance and
block horizontal scaling and scale-to-zero. The updated MCP spec’s **stateless Streamable HTTP**
turns each call into an independent request — so an MCP server becomes an ordinary, cloud-native
microservice. Pair that with Quarkus (AOT compilation, reactive routing, millisecond startup, small
heap) and you get high-density AI infrastructure: many specialized, memory-efficient tool servers
that start instantly and cost nothing when idle.

---

## Automated dependency upgrades

This repo keeps its Quarkus platform and extensions current automatically:

- **`.github/dependabot.yml`** runs a **daily** check for Maven (`io.quarkus*`, `io.quarkiverse.mcp*`,
  grouped) and GitHub Actions updates.
- **`.github/workflows/dependabot-auto-merge.yml`** builds and tests each Dependabot PR, then
  **auto-approves and auto-merges** it — but only if the build and tests pass.

---

## Project layout

```
stateless-mcp-quarkus/
├── pom.xml
├── compose.yml                   # Postgres + 5 replicas + nginx load balancer
├── start-cluster.sh              # build + launch the cluster demo
├── stop-cluster.sh               # tear it down
├── deploy-openshift.sh           # build native image + deploy to OpenShift
├── src/main/java/dev/helios/
│   ├── ShipmentTools.java        # @Tool methods — the MCP surface (+ getServerInstance)
│   ├── ShipmentService.java      # stateless business logic (Panache DB lookups)
│   ├── InstanceInfo.java         # per-replica identity (makes statelessness visible)
│   ├── domain/                   # JPA/Panache entities (Shipment, Inventory, …)
│   └── model/                    # response records (MCP/JSON output)
├── src/main/resources/
│   ├── application.properties    # stateless MCP + Dev Services (dev/test) + OpenShift
│   ├── import.sql                # seed data for Dev Services / tests
│   └── META-INF/resources/index.html   # the console SPA (pure MCP client)
├── src/main/docker/Dockerfile.jvm
├── compose/
│   ├── init.sql                  # schema + seed for the shared cluster Postgres
│   └── nginx.conf                # round-robin across the 5 replicas
├── k8s/
│   ├── postgres.yaml             # shared Postgres (Secret + Deployment + Service)
│   └── knative-service.yaml      # scale-to-zero Knative Service
├── src/test/java/dev/helios/HeliosMcpTest.java   # tests over the /mcp endpoint
└── .github/                      # Dependabot + build-gated auto-merge workflow
```

Built with [Quarkus](https://quarkus.io) and the
[Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server) extension.
