#!/usr/bin/env bash
# Build both apps + images and launch the full topology:
#   browser → agent (:8080) → LB → 5 stateless MCP servers → shared Postgres
# Then open http://localhost:8080 and use "Run 5x" to watch the serving MCP
# replica rotate while results stay identical.
set -euo pipefail
cd "$(dirname "$0")"

# Resolve defaults in Bash before invoking Compose. Some podman-compose versions
# pass Compose's ${VAR:-default} form through literally, which makes Quarkus try
# to expand HELIOS_LLM_MODEL recursively inside the agent container.
: "${OPENAI_API_KEY:?ERROR: set OPENAI_API_KEY in your shell before start-cluster.sh}"
export HELIOS_LLM_MODEL="${HELIOS_LLM_MODEL:-gpt-5.6-sol}"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker; COMPOSE=(docker compose)
  export MCP_IMAGE="helios-mcp:latest"; export AGENT_IMAGE="helios-agent:latest"
elif command -v podman >/dev/null 2>&1; then
  ENGINE=podman; COMPOSE=(podman compose)
  export MCP_IMAGE="localhost/helios-mcp:latest"; export AGENT_IMAGE="localhost/helios-agent:latest"
else
  echo "ERROR: need docker or podman on PATH." >&2; exit 1
fi
echo "==> Using $ENGINE"

echo "==> Building both apps (fast-jar)"
./mvnw -q clean package -DskipTests

echo "==> Building images"
"$ENGINE" build -t helios-mcp:latest   -f mcp-server/src/main/docker/Dockerfile.jvm mcp-server
"$ENGINE" build -t helios-agent:latest -f agent/src/main/docker/Dockerfile.jvm       agent
if [ "$ENGINE" = "podman" ]; then
  podman tag helios-mcp:latest   "$MCP_IMAGE"   2>/dev/null || true
  podman tag helios-agent:latest "$AGENT_IMAGE" 2>/dev/null || true
fi

echo "==> Starting Postgres + 5 MCP replicas + LB + agent"
"${COMPOSE[@]}" up -d

echo "==> Waiting for the agent health endpoint"
READY=false
for _ in {1..45}; do
  if curl --fail --silent --output /dev/null http://localhost:8080/q/health/ready; then
    READY=true
    break
  fi
  sleep 1
done

if [ "$READY" != true ]; then
  echo "ERROR: agent did not become ready at http://localhost:8080 within 45 seconds." >&2
  "${COMPOSE[@]}" ps
  "${COMPOSE[@]}" logs --tail=100 agent
  exit 1
fi

echo
echo "==> Helios Control Tower (agent) is ready at:  http://localhost:8080"
echo "    Pick a tool and click 'Run 5x' to watch requests rotate across the"
echo "    5 stateless MCP replicas. Tear down with ./stop-cluster.sh"
