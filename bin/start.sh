#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

if [[ "$INSTANCE" = "localhost" ]]; then
  ##use eme.sh restart .
  $SCRIPT_DIR/../bin/eme.sh start "$SCRIPT_DIR/.."  
else
    sudo docker start ${INSTANCE}
    sleep 5
    sudo docker logs -f --tail 500 ${INSTANCE}
fi



