#!/bin/bash

# Sourced by start.sh and stop.sh: sets REMOTE_CONTROL_PORT from "remoteControl.port" in
# workspace.code-workspace (JSONC, so jq cannot parse it). Falls back to the extension's default, 3710.
REMOTE_CONTROL_PORT=$(sed -n 's/^[[:space:]]*"remoteControl\.port"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
  "$(dirname "${BASH_SOURCE[0]}")/../workspace.code-workspace" 2>/dev/null | head -1)
REMOTE_CONTROL_PORT=${REMOTE_CONTROL_PORT:-3710}
