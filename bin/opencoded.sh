#!/usr/bin/env bash
#
# opencoded.sh — start the OpenCode web interface on 127.0.0.1:49374
# and open it in the local browser.
#
# Uses a fixed password (never rotates), so sign-in is always:
#   username: opencode
#   password: $OPENCODE_PASSWORD (default below)
#
# Idempotent: if the background service is already listening at the target
# address with the right config, it is left alone.
#
set -euo pipefail

HOST="127.0.0.1"
PORT="49374"
PASSWORD="${OPENCODE_PASSWORD:-FPsl-1sc-reGPpldCdSKgOkgmsX31g-cFo_IldXRZAc}"
URL="http://${HOST}:${PORT}"

# Locate the opencode binary
if command -v opencode >/dev/null 2>&1; then
  OPENCODE="opencode"
elif [[ -x "$HOME/.opencode/bin/opencode" ]]; then
  OPENCODE="$HOME/.opencode/bin/opencode"
else
  echo "error: opencode not found in PATH or ~/.opencode/bin" >&2
  exit 1
fi

# exits 0 when the port answers any HTTP response
alive() {
  curl -s -o /dev/null --max-time 3 "$URL/"
}

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
