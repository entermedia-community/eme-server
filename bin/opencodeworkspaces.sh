#!/usr/bin/env bash
#
# opencodeworkspaces.sh — manage the OpenCode service that runs the My EME per-user workspaces
#
#   opencodeworkspaces.sh [start]   start the service if needed
#   opencodeworkspaces.sh restart   restart the service
#   opencodeworkspaces.sh stop      stop the service
#   opencodeworkspaces.sh status    show service status
#
# This is the launcher the Docker container runs (bin/eme.sh restartopencode, bin/start.sh). It
# is a copy of bin/opencoded.sh made for unattended use: there is no browser pairing link.
#
# The service uses the global config opencodeglobal.json in the repo root (the same file bin/opencoded.sh
# links); hostname and port come from its "server" block. Every My EME user works in their own folder
# under ~/workspaces/<userid>/:
#
#   ~/workspaces/<userid>/                where the sessions work (the user's files)
#   ~/workspaces/<userid>/opencode.json   that user's own config (made by the EME server from
#                                         webapp/system/workspaces/opencodeuser.json; sessions cannot edit it)
#   ~/workspaces/<userid>/.agents/skills  that user's own skills
#
# The shared skills every user gets are in webapp/system/workspaces/skills. The user config names them
# with {env:EME_WORKSPACE_SKILLS}, which this script sets on the service.
#
# Optional settings (from .env or the caller's environment):
#   OPENCODE_HOST / OPENCODE_PORT   override the "server" block of the config
#   OPENCODE_PASSWORD               fixed service password. Without it OpenCode makes a random one
#                                   when the service is first set up; the aiserver row "opencode"
#                                   (serverapikey = base64 of opencode:<password>) must match.
#
# Idempotent: if the background service is already listening at the target address with the right
# config, it is left alone.
#
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)

if [[ -f "$SCRIPT_DIR/../.env" ]]; then
  set -a
  source "$SCRIPT_DIR/../.env"
  set +a
fi

ACTION="${1:-start}"
case "$ACTION" in
  start|restart|stop|status) ;;
  *)
    echo "usage: $(basename "$0") [start|restart|stop|status]" >&2
    exit 1
    ;;
esac

# The global config is in the repo root. The shared skills live in webapp/system (a link to plugins/system/html);
# use the real path so the service sees it.
CONFIG="$(cd "$SCRIPT_DIR/.." && pwd)/opencodeglobal.json"
SKILLS_DIR="$(readlink -f "$SCRIPT_DIR/../webapp/system/workspaces/skills")"

if [[ ! -f "$CONFIG" ]]; then
  echo "error: config not found: $CONFIG" >&2
  exit 1
fi

# Read the service binding from the "server" block of the global config
if command -v jq >/dev/null 2>&1; then
  HOST="$(jq -r '.server.hostname // empty' "$CONFIG")"
  PORT="$(jq -r '.server.port // empty' "$CONFIG")"
elif command -v python3 >/dev/null 2>&1; then
  read -r HOST PORT < <(python3 -c '
import json,sys
c=json.load(open(sys.argv[1]))
s=c.get("server",{})
print(s.get("hostname",""), s.get("port",""))' "$CONFIG")
else
  echo "error: need jq or python3 to read $CONFIG" >&2
  exit 1
fi
HOST="${OPENCODE_HOST:-$HOST}"
PORT="${OPENCODE_PORT:-$PORT}"

if [[ -z "$HOST" || -z "$PORT" ]]; then
  echo "error: server.hostname / server.port missing in $CONFIG (or set OPENCODE_HOST / OPENCODE_PORT)" >&2
  exit 1
fi
URL="http://${HOST}:${PORT}"

# Locate the opencode binary
if command -v opencode >/dev/null 2>&1; then
  OPENCODE="opencode"
elif [[ -x "$HOME/.opencode/bin/opencode" ]]; then
  OPENCODE="$HOME/.opencode/bin/opencode"
else
  echo "error: opencode not found in PATH or ~/.opencode/bin" >&2
  echo "  curl -fsSL https://opencode.ai/v2/install | bash"
  exit 1
fi

# exits 0 when the port answers any HTTP response
alive() {
  curl -s -o /dev/null --max-time 3 "$URL/"
}

case "$ACTION" in
  stop)
    "$OPENCODE" service stop
    exit 0
    ;;
  status)
    "$OPENCODE" service status || true
    if alive; then
      echo "web interface: $URL (up)"
    else
      echo "web interface: $URL (down)"
      exit 3
    fi
    exit 0
    ;;
esac

# The user folders live in the running user's home (in Docker: /home/entermedia/workspaces)
mkdir -p "$HOME/workspaces"

# The global config is a link to the project config, so the background service
# (which runs from $HOME) always uses it
GLOBAL_DIR="${OPENCODE_CONFIG_DIR:-${XDG_CONFIG_HOME:-$HOME/.config}/opencode}"
GLOBAL_CONFIG="$GLOBAL_DIR/opencode.jsonc"
config_changed=0
if [[ "$(readlink "$GLOBAL_CONFIG" 2>/dev/null)" != "$CONFIG" ]]; then
  mkdir -p "$GLOBAL_DIR"
  for f in "$GLOBAL_DIR/opencode.json" "$GLOBAL_CONFIG"; do
    if [[ -e "$f" && ! -L "$f" ]]; then
      echo "backing up $f -> $f.bak"
      mv "$f" "$f.bak"
    fi
  done
  rm -f "$GLOBAL_DIR/opencode.json"
  ln -sfn "$CONFIG" "$GLOBAL_CONFIG"
  echo "linked $GLOBAL_CONFIG -> $CONFIG"
  config_changed=1
fi

# Only change a setting when it differs, since changing one stops the service
setting() { "$OPENCODE" service get "$1" 2>/dev/null | tr -d '[:space:]' || true; }

if [[ "$(setting hostname)" != "$HOST" ]]; then
  echo "setting hostname -> $HOST (stops the service)"
  "$OPENCODE" service set hostname "$HOST"
fi
if [[ "$(setting port)" != "$PORT" ]]; then
  echo "setting port -> $PORT (stops the service)"
  "$OPENCODE" service set port "$PORT"
fi
if [[ -n "${OPENCODE_PASSWORD:-}" && "$(setting password)" != "$OPENCODE_PASSWORD" ]]; then
  echo "setting password (stops the service)"
  "$OPENCODE" service set password "$OPENCODE_PASSWORD"
fi

# Tell the service where the shared skills are (the config names it as {env:EME_WORKSPACE_SKILLS})
if [[ "$("$OPENCODE" service get env EME_WORKSPACE_SKILLS 2>/dev/null | tr -d '[:space:]' || true)" != "$SKILLS_DIR" ]]; then
  echo "setting EME_WORKSPACE_SKILLS -> $SKILLS_DIR (stops the service)"
  "$OPENCODE" service set env EME_WORKSPACE_SKILLS "$SKILLS_DIR"
fi

# Start the service if it is not already listening
if [[ "$ACTION" == restart || "$config_changed" == 1 ]] && alive; then
  echo "restarting OpenCode background service..."
  "$OPENCODE" service restart
fi
if ! alive; then
  echo "starting OpenCode background service..."
  "$OPENCODE" service start
  for _ in $(seq 1 30); do
    alive && break
    sleep 1
  done
fi

if ! alive; then
  echo "error: service did not come up at $URL (see 'opencode service status')" >&2
  exit 1
fi

echo "OpenCode workspaces service: $URL"
