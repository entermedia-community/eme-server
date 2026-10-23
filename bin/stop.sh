#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

if [[ "$INSTANCE" = "localhost" ]]; then
  SERVER_HOME=$(cd "$SCRIPT_DIR/.." && pwd)
  source "$SCRIPT_DIR/remotecontrolport.sh"

  # A Tomcat launched from the VS Code debugger shows catalina.base on its command line
  DEBUG_PATTERN="catalina\.base=$SERVER_HOME/tomcat( |$)"

  if pgrep -f "$DEBUG_PATTERN" >/dev/null; then
    if node -e '
      const ws = new WebSocket("ws://127.0.0.1:" + process.argv[1]);
      ws.onopen = () => { ws.send(JSON.stringify({ command: "workbench.action.debug.stop" })); setTimeout(() => process.exit(0), 200); };
      ws.onerror = () => process.exit(1);
    ' "$REMOTE_CONTROL_PORT" 2>/dev/null; then
      echo "*** Sent stop to VS Code debug session"
      exit 0
    fi

    echo "ERROR: VS Code is not reachable on port $REMOTE_CONTROL_PORT. Run bin/start.sh to set up the Remote Control extension." >&2
    exit 1
  fi

  exec "$SCRIPT_DIR/../bin/eme.sh" stop "$SERVER_HOME"
else
  sudo docker stop -t 60 "$INSTANCE"
fi


