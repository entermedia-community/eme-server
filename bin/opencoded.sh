#!/usr/bin/env bash
#
# opencoded.sh — manage the OpenCode web interface on 127.0.0.1:49374
#
#   opencoded.sh [start]   configure + start the service, open the browser
#   opencoded.sh restart   configure + restart the service
#   opencoded.sh stop      stop the service
#   opencoded.sh status    show service status
#
# Uses a fixed password (never rotates), so sign-in is always:
#   username: opencode
#   password: $OPENCODE_PASSWORD (default below)
#
# Idempotent: if the background service is already listening at the target
# address with the right config, it is left alone.
#
set -euo pipefail

ACTION="${1:-start}"
case "$ACTION" in
  start|restart|stop|status) ;;
  *)
    echo "usage: $(basename "$0") [start|restart|stop|status]" >&2
    exit 1
    ;;
esac

HOST="127.0.0.1"
PORT="49374"
PASSWORD="${OPENCODE_PASSWORD:-FPsl-1sc-reGPpldCdSKgOkgmsX31g-cFo_IldXRZAc}"
URL="http://${HOST}:${PORT}"
# opencode.json lives one folder up from this script (the repo root)
CONFIG="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/opencode.json"

if [[ ! -f "$CONFIG" ]]; then
  echo "error: config not found: $CONFIG" >&2
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
current_pw=$("$OPENCODE" service get password 2>/dev/null | tr -d '[:space:]' || true)

if [[ "$current_host" != "$HOST" ]]; then
  echo "setting hostname: ${current_host:-<default>} -> $HOST (stops the service)"
  "$OPENCODE" service set hostname "$HOST"
fi
if [[ "$current_port" != "$PORT" ]]; then
  echo "setting port: ${current_port:-<default>} -> $PORT (stops the service)"
  "$OPENCODE" service set port "$PORT"
fi
if [[ "$current_pw" != "$PASSWORD" ]]; then
  echo "setting fixed password (stops the service)"
  "$OPENCODE" service set password "$PASSWORD"
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
echo "sign-in: username=opencode password=$PASSWORD"

[[ "$ACTION" == start ]] || exit 0

# Open the local browser (best effort — headless environments just get a warning)
if command -v xdg-open >/dev/null 2>&1; then
  xdg-open "$URL" >/dev/null 2>&1 || echo "warning: xdg-open failed, open $URL manually" >&2
elif command -v google-chrome >/dev/null 2>&1; then
  google-chrome "$URL" >/dev/null 2>&1 || echo "warning: google-chrome failed, open $URL manually" >&2
elif command -v firefox >/dev/null 2>&1; then
  firefox "$URL" >/dev/null 2>&1 || echo "warning: firefox failed, open $URL manually" >&2
else
  echo "no browser launcher found (xdg-open/chrome/firefox); open $URL manually" >&2
fi
