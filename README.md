# Helios Control Tower — Agent + Stateless MCP Fleet on Quarkus

A production-shaped demo of **stateless, cloud-native MCP (Model Context Protocol) servers** on
**Quarkus** / **Java 25**, fronted by a separate **agent** app (an MCP client) with a polished
single-page console.

> Companion demo for the talk **“Going Stateless: Scaling MCP Servers to Cloud-Native Java & HTTP”**
> (MCP Dev Summit, Toronto 2026).

---

## The enterprise scenario

**Helios Logistics** is a fictional global freight company. Its AI agents and ops teams query live
enterprise data — shipment tracking, warehouse inventory, lane pricing, carrier SLAs, open
exceptions. Instead of one stateful, session-heavy server, Helios runs a **fleet of stateless MCP
tool servers**, and a separate **agent** app talks to them:

```
browser SPA ─▶ Agent app ─▶ MCP client ─▶ Load balancer ─▶ 5 stateless MCP servers ─▶ shared Postgres
              (this UI)     (McpClient)     (nginx / K8s Svc)   (identical replicas)
```

The two tiers have clean responsibilities:

- **`agent/`** — the front door. Serves the console SPA and holds a **managed `McpClient`**
  (`quarkus-langchain4j-mcp`) that speaks MCP to the fleet. This is the *MCP client* tier.
- **`mcp-server/`** — pure MCP tool servers: `@Tool` methods + PostgreSQL lookups, no UI. Run as
  **5 identical stateless replicas** behind the load balancer.

Because the servers keep **zero** in-JVM state (all state lives in Postgres), each `tools/call` can
be served by any replica. The console's **“Run 5×”** shows the serving replica rotating while the
result never changes — real horizontal scaling, and the basis for **scale to zero**.

Statelessness is enabled with `quarkus.mcp.server.http.streamable.auto-init=true`: no `initialize`
handshake or session is required, so there's no affinity to pin a client to one replica.

---

## MCP tools

| Tool | What it does | Example args |
|------|--------------|--------------|
| `getShipmentStatus` | Live status, carrier, route, ETA for a shipment | `HLX-10032291` |
| `getWarehouseInventory` | On-hand / reserved / available stock for a SKU | `SKU-COLD-4521` |
| `estimateDelivery` | Transit days & cost for a lane between two hubs | `FRA → YYZ, 620 kg` |
| `getCarrierSla` | On-time %, transit days, damage rate for a carrier | `HELIOS-AIR` |
| `listOpenExceptions` | Shipments needing operator attention in a region | `APAC` |

Arguments are hardened with Jakarta Bean Validation (`@Pattern`, `@Positive`, `@Size`) — malformed
input is rejected before any DB lookup. (A `getServerInstance` tool also exists, used by the console
to reveal which replica served a request.)

---

## Run it — dev mode (two processes)

Prerequisites: **Java 25+**, **Maven 3.9+**, and **Docker** or **Podman** (for the database).

```bash
# Terminal 1 — the MCP server. Dev Services auto-starts a throwaway PostgreSQL
# (seeded from import.sql) and serves MCP on :8080.
mvn -pl mcp-server quarkus:dev

# Terminal 2 — the agent (console + MCP client) on :8090, pointing at :8080.
mvn -pl agent quarkus:dev
```

Open the console at **<http://localhost:8090/>**. In dev there is one MCP server, so “Run 5×” shows
a single replica — same stateless guarantee, one server.

Other endpoints: MCP at `http://localhost:8080/mcp`, health at `/q/health` on each app.

## Run it — the cluster (see real statelessness)

Launch the full topology — **agent → LB → 5 stateless MCP replicas → shared Postgres**:

```bash
./start-cluster.sh      # builds both images, starts everything
open http://localhost:8080
# Pick a tool → "Run 5×" → the serving replica rotates across all 5, results identical.
./stop-cluster.sh       # tear down
```

## Package & native

```bash
mvn clean package                    # builds both modules
mvn clean package -Dnative           # GraalVM native — instant start, tiny memory
```

## Deploy to OpenShift / Kubernetes (native + scale-to-zero)

The `quarkus-openshift` extension generates the manifests at build time. The **agent** gets a Route
(external); the **MCP servers** stay internal behind a ClusterIP Service that round-robins across
replicas — no nginx needed on the cluster.

```bash
oc login ... && oc new-project helios
./deploy-openshift.sh            # native images; or: ./deploy-openshift.sh jvm

# Scale the stateless fleet — no session affinity to worry about:
oc scale deploy/stateless-mcp-server --replicas=8
```

The agent finds the fleet at `http://stateless-mcp-server:8080/mcp` (override with `MCP_FLEET_URL`).
Datasource comes from the `helios-db` Secret — no secrets in the image. Manifests land in each
module's `target/kubernetes/`.

**Scale to zero with Knative** (OpenShift Serverless) — the ultimate stateless payoff. A native
image cold-starts in tens of milliseconds, so running zero replicas when idle is practical:

```bash
oc apply -f k8s/postgres.yaml
oc apply -f k8s/knative-service.yaml   # stateless MCP servers, minScale: 0
```

> Demo flow for the talk: **dev (2 processes) → local cluster (`./start-cluster.sh`, 5 replicas
> round-robin) → OpenShift native → Knative scale-to-zero.**

---

## Automated dependency upgrades

- **`.github/dependabot.yml`** — **daily** Maven (`io.quarkus*`, `io.quarkiverse.mcp*`, grouped) and
  GitHub Actions checks.
- **`.github/workflows/dependabot-auto-merge.yml`** — builds/tests each Dependabot PR, then
  **auto-merges** only if it passes.

## Project layout

```
stateless-mcp-quarkus/               (parent / aggregator pom)
├── mcp-server/                      # stateless MCP tool servers (run as 5 replicas)
│   ├── src/main/java/dev/helios/
│   │   ├── ShipmentTools.java       #   @Tool methods (+ getServerInstance)
│   │   ├── ShipmentService.java     #   stateless business logic (Panache)
│   │   ├── InstanceInfo.java        #   per-replica identity
│   │   ├── domain/ , model/         #   JPA entities + response records
│   │   └── ...
│   ├── src/main/resources/          #   application.properties, import.sql
│   ├── src/main/docker/Dockerfile.jvm
│   └── src/test/java/...HeliosMcpTest.java   # tests over /mcp
├── agent/                           # MCP client + console (the front door)
│   ├── src/main/java/dev/helios/agent/
│   │   ├── AgentService.java        #   managed McpClient → the fleet
│   │   └── AgentResource.java       #   REST the SPA calls (/agent/*)
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   └── META-INF/resources/index.html   # the console SPA
│   └── src/main/docker/Dockerfile.jvm
├── compose.yml                      # postgres + 5 mcp replicas + nginx LB + agent
├── compose/ (init.sql, nginx.conf)
├── k8s/ (postgres.yaml, knative-service.yaml)
├── start-cluster.sh / stop-cluster.sh / deploy-openshift.sh
└── .github/                         # Dependabot + build-gated auto-merge
```

Built with [Quarkus](https://quarkus.io), the
[Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server), and
[Quarkus LangChain4j MCP](https://docs.quarkiverse.io/quarkus-langchain4j/dev/) client.
