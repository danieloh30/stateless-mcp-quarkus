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

The console UI makes statelessness visible: every response is stamped with the **instance** that
served it, and a **“Run 5×”** button fires independent requests to show there is no session in play.

---

## Architecture

```
  AI Agent (Goose / Claude)            Browser Console (SPA)
          │  MCP / JSON-RPC                     │  REST
          ▼  (Streamable HTTP, stateless)       ▼
   ┌───────────────────────────────────────────────────────┐
   │                Load balancer / K8s Service             │
   └───────────────────────────────────────────────────────┘
        │              │              │
        ▼              ▼              ▼        (scale to zero when idle)
   ┌─────────┐    ┌─────────┐    ┌─────────┐
   │ Quarkus │    │ Quarkus │    │   ...   │   Stateless MCP replicas
   │  :8080  │    │  :8080  │    │         │   (ShipmentTools + ShipmentService)
   └─────────┘    └─────────┘    └─────────┘
        │
        ▼
   Enterprise backends (DB lookups / internal API proxies)
```

Everything an agent needs is exposed as `@Tool` methods over MCP; the same stateless business
logic is reused by a thin REST facade so the browser console can drive the demo.

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

## Run it

Prerequisites: **Java 25+** and **Maven 3.9+**.

```bash
# Dev mode with live reload
mvn quarkus:dev
```

Then open:

- 🖥️  **Console UI** — <http://localhost:8080/>
- 🔌 **MCP endpoint** — `http://localhost:8080/mcp` (Streamable HTTP)
- ❤️  **Health** — <http://localhost:8080/q/health>

Build a runnable jar:

```bash
mvn clean package
java -jar target/quarkus-app/quarkus-run.jar
```

Build a GraalVM native executable (instant startup, minimal memory — ideal for scale-to-zero):

```bash
mvn clean package -Dnative
```

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
├── src/main/java/dev/helios/
│   ├── ShipmentTools.java        # @Tool methods — the MCP surface
│   ├── ShipmentService.java      # stateless business logic + reference data
│   ├── ConsoleResource.java      # thin REST facade for the SPA
│   ├── InstanceInfo.java         # per-replica identity (makes statelessness visible)
│   └── model/                    # response records
├── src/main/resources/
│   ├── application.properties
│   └── META-INF/resources/index.html   # the console SPA
├── src/test/java/dev/helios/HeliosMcpTest.java
└── .github/                      # Dependabot + auto-merge workflow
```

Built with [Quarkus](https://quarkus.io) and the
[Quarkus MCP Server](https://github.com/quarkiverse/quarkus-mcp-server) extension.
