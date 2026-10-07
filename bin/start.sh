#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

if [[ "$INSTANCE" = "localhost" ]]; then
  SERVER_HOME=$(cd "$SCRIPT_DIR/.." && pwd)

  # A Tomcat launched from the VS Code debugger shows catalina.base on its command line
  DEBUG_PATTERN="catalina\.base=$SERVER_HOME/tomcat( |$)"
  if pgrep -f "$DEBUG_PATTERN" >/dev/null; then
    echo "*** VS Code debug session is already running, use bin/restart.sh"
    exit 0
  fi

  # Install the Remote Control extension on demand; VS Code activates it without a reload
  REMOTE_CONTROL=eliostruyf.vscode-remote-control
  if command -v code >/dev/null && ! code --list-extensions 2>/dev/null | grep -qix "$REMOTE_CONTROL"; then
    echo "*** Installing VS Code extension $REMOTE_CONTROL so scripts can drive the debugger"
    code --install-extension "$REMOTE_CONTROL" >/dev/null 2>&1
    for _ in $(seq 1 20); do
      (exec 3<>/dev/tcp/127.0.0.1/3710) 2>/dev/null && break
      sleep 0.5
    done
  fi

  # Start the debug session through Remote Control (port in .vscode/settings.json)
  if node -e '
    const ws = new WebSocket("ws://127.0.0.1:3710");
    ws.onopen = () => { ws.send(JSON.stringify({ command: "workbench.action.debug.start" })); setTimeout(() => process.exit(0), 200); };
    ws.onerror = () => process.exit(1);
  ' 2>/dev/null; then
    echo "*** Sent start to VS Code debug session"
  else
    echo "ERROR: VS Code is not reachable on port 3710. Open this workspace in VS Code with $REMOTE_CONTROL enabled." >&2
    exit 1
  fi
else
    sudo docker start ${INSTANCE}
    sleep 5
    sudo docker logs -f --tail 500 ${INSTANCE}
fi
