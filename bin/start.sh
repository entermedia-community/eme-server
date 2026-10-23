#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

# bin/restart.sh calls this script with "restart"; default is "start"
MODE=${1:-start}

if [[ "$INSTANCE" = "localhost" ]]; then
  SERVER_HOME=$(cd "$SCRIPT_DIR/.." && pwd)
  source "$SCRIPT_DIR/remotecontrolport.sh"

  # A Tomcat launched from the VS Code debugger shows catalina.base on its command line
  DEBUG_PATTERN="catalina\.base=$SERVER_HOME/tomcat( |$)"
  RUNNING=0
  pgrep -f "$DEBUG_PATTERN" >/dev/null && RUNNING=1

  if [[ "$MODE" = "start" ]]; then
    if [[ $RUNNING -eq 1 ]]; then
      echo "*** VS Code debug session is already running, use bin/restart.sh"
      exit 0
    fi

    # Install the Remote Control extension on demand; VS Code activates it without a reload
    REMOTE_CONTROL=eliostruyf.vscode-remote-control
    if command -v code >/dev/null && ! code --list-extensions 2>/dev/null | grep -qix "$REMOTE_CONTROL"; then
      echo "*** Installing VS Code extension $REMOTE_CONTROL so scripts can drive the debugger"
      code --install-extension "$REMOTE_CONTROL" >/dev/null 2>&1
      for _ in $(seq 1 20); do
        (exec 3<>/dev/tcp/127.0.0.1/$REMOTE_CONTROL_PORT) 2>/dev/null && break
        sleep 0.5
      done
    fi
    DEBUG_COMMAND="workbench.action.debug.start"
  else
    # No debug session yet, so start one instead
    [[ $RUNNING -eq 1 ]] || exec "$SCRIPT_DIR/start.sh"
    DEBUG_COMMAND="workbench.action.debug.restart"
  fi

  # Start/restart the debug session through Remote Control (port from workspace.code-workspace)
  if node -e '
    const ws = new WebSocket("ws://127.0.0.1:" + process.argv[2]);
    ws.onopen = () => { ws.send(JSON.stringify({ command: process.argv[1] })); setTimeout(() => process.exit(0), 200); };
    ws.onerror = () => process.exit(1);
  ' "$DEBUG_COMMAND" "$REMOTE_CONTROL_PORT" 2>/dev/null; then
    echo "*** Sent $MODE to VS Code debug session"
  else
    echo "ERROR: VS Code is not reachable on port $REMOTE_CONTROL_PORT. Open this workspace in VS Code with the Remote Control extension enabled." >&2
    exit 1
  fi
else
  if [[ "$MODE" = "start" ]]; then
    sudo docker start ${INSTANCE}
  else
    sudo docker stop -t 60 $INSTANCE && sudo docker start $INSTANCE
  fi

  #if OPENCODE_ENABLED is true, additional logic could be added here
  if [[ "$OPENCODE_ENABLED" = "true" ]]; then
    sleep 2
    echo "*** Starting OPENCODE"
    #Run inside docker /home/entermedia/eme-server/bin/opencoded.sh with entermedia user
    sudo docker exec -u entermedia -it $INSTANCE /home/entermedia/eme-server/bin/opencoded.sh
    echo ""
  fi

  sleep 5
  sudo docker logs -f --tail 500 ${INSTANCE}
fi
