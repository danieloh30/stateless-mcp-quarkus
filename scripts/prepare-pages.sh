#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
pages_source="$repo_root/.pages-src"

rm -rf "$pages_source"
mkdir -p "$pages_source/images" "$pages_source/stylesheets"

sed \
  -e 's#](docs/images/#](images/#g' \
  -e 's#](\.github/#](https://github.com/danieloh30/stateless-mcp-quarkus/blob/main/.github/#g' \
  "$repo_root/README.md" > "$pages_source/index.md"

cp "$repo_root"/docs/images/* "$pages_source/images/"
cp "$repo_root/docs/stylesheets/extra.css" "$pages_source/stylesheets/extra.css"
