#!/usr/bin/env bash
#
# opencode.sh — start opencode with a fresh EME Bearer token in EME_TOKEN.
#
# opencodeglobal.json (linked as the global config by bin/opencoded.sh) sends "Bearer {env:EME_TOKEN}" to the local MCP server, and opencode only reads
# the variable at startup. Run this instead of `opencode` so the token is always current.
#
#   bin/opencode.sh [opencode args]
#
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "$0")" && pwd)

EME_TOKEN=$("$SCRIPT_DIR/refreshtoken.sh" | tail -n1)
export EME_TOKEN

cd "$SCRIPT_DIR/.."
exec opencode "$@"
