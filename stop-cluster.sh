#!/usr/bin/env bash
# Tear down the cluster demo (replicas, load balancer, and Postgres).
set -euo pipefail
cd "$(dirname "$0")"

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  COMPOSE=(docker compose)
else
  COMPOSE=(podman compose)
fi

echo "==> Stopping and removing the cluster (including the database volume)"
"${COMPOSE[@]}" down -v
echo "==> Done."
