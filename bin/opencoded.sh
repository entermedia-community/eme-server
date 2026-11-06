#!/usr/bin/env bash
#
# opencoded.sh — manage the OpenCode web interface
#
#   opencoded.sh [start]   start the service if needed, log in via the browser
#   opencoded.sh restart   restart the service, log in via the browser
#   opencoded.sh stop      stop the service
#   opencoded.sh status    show service status
#
# hostname and port come from the "server" block in opencode.json (one folder
# up from this script). There is no fixed password: sign-in happens in the
# browser through a one-time pairing link (`opencode pair`), which the script
# prints and opens. Links work once and expire in 5 minutes — re-run the
# script to get a fresh one.
#
# OPENCODE_PAIR_URL (optional): use this external HTTP(S) origin in the
# pairing links, e.g. when the service is served behind nginx.
#
# Idempotent: if the background service is already listening at the target
# address with the right config, it is left alone.
#
set -euo pipefail

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a


ACTION="${1:-start}"
case "$ACTION" in
  start|restart|stop|status) ;;
  *)
    echo "usage: $(basename "$0") [start|restart|stop|status]" >&2
    exit 1
    ;;
esac

# opencode.json lives one folder up from this script (the repo root)
CONFIG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/opencode.json"

if [[ ! -f "$CONFIG" ]]; then
  echo "error: config not found: $CONFIG" >&2
  exit 1
fi

# Read the service binding from the "server" block in opencode.json.
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

URL="http://${HOST}:${PORT}"

if [[ -z "$HOST" || -z "$PORT" ]]; then
  echo "error: server.hostname / server.port missing in $CONFIG or .env" >&2
  exit 1
fi

# Locate the opencode binary
if command -v opencode >/dev/null 2>&1; then
  OPENCODE="opencode"
elif [[ -x "$HOME/.opencode/bin/opencode" ]]; then
  OPENCODE="$HOME/.opencode/bin/opencode"
else
  echo "error: opencode not found in PATH or ~/.opencode/bin" >&2
  echo "  curl -fsSL https://opencode.ai/v2/install | bash"
  #echo "  curl -fsSL https://opencode.ai/install | bash"
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

# The global config is a symlink to the project config, so the background
# service (which runs from $HOME) always uses it
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

# Only change config when it differs — changing a setting stops the server
current_host=$("$OPENCODE" service get hostname 2>/dev/null | tr -d '[:space:]' || true)
current_port=$("$OPENCODE" service get port 2>/dev/null | tr -d '[:space:]' || true)

if [[ "$current_host" != "$HOST" ]]; then
  echo "setting hostname: ${current_host:-<default>} -> $HOST (stops the service)"
  "$OPENCODE" service set hostname "$HOST"
fi
if [[ "$current_port" != "$PORT" ]]; then
  echo "setting port: ${current_port:-<default>} -> $PORT (stops the service)"
  "$OPENCODE" service set port "$PORT"
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

echo "OpenCode web interface: $URL"

[[ "$ACTION" == start || "$ACTION" == restart ]] || exit 0

# Log in through the browser: print a fresh one-time pairing link and open it.
PAIR_ARGS=()
if [[ -n "${OPENCODE_PAIR_URL:-}" ]]; then
  PAIR_ARGS+=(--url "$OPENCODE_PAIR_URL")
fi
PAIR_OUT=$("$OPENCODE" pair "${PAIR_ARGS[@]}" 2>&1) || {
  echo "error: 'opencode pair' failed:" >&2
  echo "$PAIR_OUT" >&2
  exit 1
}
echo ""
echo "$PAIR_OUT"

# Prefer the loopback link for a local browser; fall back to the first one.
LINK=$(grep -Eo 'https?://[^ ]+' <<<"$PAIR_OUT" | grep -E '://(127\.0\.0\.1|localhost)(:|/)' | head -n1 || true)
LINK=${LINK:-$(grep -Eo 'https?://[^ ]+' <<<"$PAIR_OUT" | head -n1 || true)}

if [[ -z "$LINK" ]]; then
  echo "error: no pairing link found in 'opencode pair' output" >&2
  exit 1
fi

# Open the local browser (best effort — headless environments just get a warning)
if command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$LINK" >/dev/null 2>&1 || echo "warning: xdg-open failed, open $LINK manually" >&2
elif command -v google-chrome >/dev/null 2>&1; then
  google-chrome "$LINK" >/dev/null 2>&1 || echo "warning: google-chrome failed, open $LINK manually" >&2
elif command -v firefox >/dev/null 2>&1; then
  firefox "$LINK" >/dev/null 2>&1 || echo "warning: firefox failed, open $LINK manually" >&2
else
  echo "no browser launcher found (xdg-open/chrome/firefox); open $LINK manually" >&2
fi
