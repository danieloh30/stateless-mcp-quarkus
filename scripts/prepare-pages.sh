#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pages_source="$repo_root/.pages-src"

rm -rf "$pages_source"
mkdir -p "$pages_source"

# Keep the published site purpose-built while all editable sources stay under docs/.
cp -R "$repo_root/docs/." "$pages_source/"
