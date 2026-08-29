# Getting started

Run Helios as two development processes for the quickest feedback, or launch the full local topology to watch requests rotate across five replicas.

## What you need

- Java 25+
- Quarkus CLI
- Docker or Podman
- An `OPENAI_API_KEY`

The model defaults to `gpt-5.6-sol`. Override it with `HELIOS_LLM_MODEL` when needed.

## Option A — dev mode

Start each module from its own directory:

=== "Terminal 1 — MCP server"

    ```bash
    cd mcp-server
    quarkus dev
    ```

=== "Terminal 2 — agent"

    ```bash
    export OPENAI_API_KEY=sk-...
    cd agent
    quarkus dev
    ```

Open [localhost:8090](http://localhost:8090/). The MCP endpoint is available at `localhost:8080/mcp`.

!!! note "One replica, same guarantee"
    Dev mode starts one MCP server. The server is still stateless; use the cluster option to make request rotation visible.

## Option B — five-replica cluster

```bash
export OPENAI_API_KEY=sk-...
./start-cluster.sh
open http://localhost:8080
```

Choose a tool and select **Run 5×**. The serving instance changes while the business result stays the same.

```bash
./stop-cluster.sh
```

## Ask a natural-language question

```bash
curl -s localhost:8090/agent/ask \
  -H 'content-type: application/json' \
  -d '{"question":"Where is shipment HLX-10032291 and will it arrive on time?"}'
```

Try inventory, delivery estimate, carrier SLA, or regional exception questions next.

## Package the project

```bash
./mvnw clean package
./mvnw clean package -Dnative
```

Continue with [Architecture](architecture.md) to understand the request path, or [Deployment](deployment.md) to move the fleet to OpenShift.
