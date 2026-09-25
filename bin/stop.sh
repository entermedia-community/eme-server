#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

if [[ "$INSTANCE" = "localhost" ]]; then
  $SCRIPT_DIR/../bin/eme.sh stop "$SCRIPT_DIR/.."  
else
    sudo docker stop -t 60 ${INSTANCE}
fi


