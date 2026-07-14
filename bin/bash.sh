#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

sudo docker exec -it ${INSTANCE} bash
