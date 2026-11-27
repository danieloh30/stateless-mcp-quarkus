# Getting started

Run Helios as two development processes for the quickest feedback, or launch the full local topology to watch requests rotate across five replicas.

## What you need

- Java 25+
- Quarkus CLI
- Docker or Podman
- An `OPENAI_API_KEY` for natural-language questions and `start-cluster.sh`

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

!!! note "The SPA runs in the agent module"
    In dev mode, `http://localhost:8080/` redirects to the MCP server's Dev UI. The console SPA
    runs at `http://localhost:8090/` and needs the second process above. Export `OPENAI_API_KEY`
    in that second terminal to enable natural-language questions. Without a key, the SPA and
    direct tool calls still work; asking the agent returns a clear configuration message. In the cluster
    setup below, the SPA uses port `8080` instead.

If dev mode appears stuck, check `curl http://localhost:8080/q/health/ready`. A response with
`"status": "UP"` means the MCP server is running. Dev mode stays in the foreground; press lowercase
`h` for keyboard shortcuts or `w` to open the current module's web page. The parent POM disables
JLine's terminal probing in dev mode because it can interfere with Quarkus's keyboard input.
Restart any dev process started before this setting was added so it takes effect.

!!! note "One replica, same guarantee"
    Dev mode starts one MCP server. The server is still stateless; use the cluster option to make request rotation visible.

## Option B — five-replica cluster

```bash
export OPENAI_API_KEY=sk-...
./start-cluster.sh
open http://localhost:8080
```

Open **2 · Fleet**, choose a tool, and select **Repeat 5 calls**. The console sends five identical, sequential business requests and compares their results. Each completed response carries its own replica evidence. A 1.2-second pause between calls makes the traffic visible; that pause is excluded from recorded call timings.

All five local replicas can serve requests. In OpenShift, five calls need not visit five distinct processes; routing and observed process counts are shown as measured.

```bash
./stop-cluster.sh
```

## Read the console

- **Ask:** enter a question, read the answer, and expand the actual specialist tool executions. Inspect a step's arguments, business result, and protocol evidence.
- **Fleet:** repeat a business lookup, compare results, or try an invalid tracking ID. Repeating five calls is disabled in single-server dev mode.
- **Protocol:** inspect allowlisted headers and the logical JSON-RPC request from the same completed tool call. The expected protocol version is separate from observed evidence.
- **Live request path:** follow the animated request and reply. Counts of ready endpoints and observed process IDs have different meanings. A last recorded reply remains historical evidence when readiness is zero.

Use **Presentation mode** for larger text, **Dark theme** for the alternate palette, **Pause observation** to stop future background readiness checks, and **Reset evidence** to clear the displayed run. The UI supports reduced-motion preferences.

## Ask a natural-language question

```bash
curl -s localhost:8090/agent/ask \
  -H 'content-type: application/json' \
  -d '{"question":"Where is shipment HLX-10032291 and will it arrive on time?"}'
```

The response includes the answer and a request-specific trace of actual tool executions. Try inventory, delivery estimate, carrier SLA, or regional exception questions next. Questions are limited to 2,000 characters and two concurrent requests; the agent has a 90-second deadline. Direct tool calls remain available when the AI provider is unavailable.

## Package the project

```bash
./mvnw clean package
./mvnw clean package -Dnative
```

## Verify changes

Run `./mvnw verify` for the Java checks. To check the console, run:

```bash
npm ci
npx playwright install chromium
npm run test:spa
```

Browser checks cover all four environments, business-result consistency, safe rendering, duplicate-submit prevention, passive observation, topology evidence, mobile layout, and reduced motion. GitHub Actions runs both Java and browser checks.

Continue with [Architecture](architecture.md) to understand the request path, or [Deployment](deployment.md) to move the fleet to OpenShift.
