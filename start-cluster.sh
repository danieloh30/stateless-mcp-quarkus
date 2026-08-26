#!/usr/bin/env bash
# Build the app + image and launch 5 stateless MCP replicas behind an nginx load
# balancer, all sharing one PostgreSQL. Then open http://localhost:8080 and use
# "Run 5x" — you'll see requests rotate across different serving instances.
set -euo pipefail
cd "$(dirname "$0")"

# Pick a container engine (Docker or Podman).
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  ENGINE=docker; COMPOSE=(docker compose); export HELIOS_IMAGE="helios-mcp:latest"
elif command -v podman >/dev/null 2>&1; then
  ENGINE=podman; COMPOSE=(podman compose); export HELIOS_IMAGE="localhost/helios-mcp:latest"
else
  echo "ERROR: need docker or podman on PATH." >&2; exit 1
fi
echo "==> Using $ENGINE"

echo "==> Building application (fast-jar)"
mvn -q clean package -DskipTests

echo "==> Building image $HELIOS_IMAGE"
"$ENGINE" build -t helios-mcp:latest -f src/main/docker/Dockerfile.jvm .
# Podman stores local images under localhost/; make the compose reference resolve.
[ "$ENGINE" = "podman" ] && podman tag helios-mcp:latest "$HELIOS_IMAGE" 2>/dev/null || true

echo "==> Starting Postgres + 5 replicas + nginx load balancer"
"${COMPOSE[@]}" up -d

echo
echo "==> Helios Control Tower is starting at:  http://localhost:8080"
echo "    Open it, pick a tool, and click 'Run 5x' to watch requests"
echo "    rotate across 5 stateless replicas. Tear down with ./stop-cluster.sh"
