#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

if [[ "$INSTANCE" = "localhost" ]]; then
  ##use eme.sh restart .
  $SCRIPT_DIR/../bin/eme.sh restart "$SCRIPT_DIR/.."  
else
  sudo docker stop -t 60 $INSTANCE && sudo docker start $INSTANCE
fi
