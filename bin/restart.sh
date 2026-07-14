#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

sudo docker stop -t 60 $INSTANCE && sudo docker start $INSTANCE
