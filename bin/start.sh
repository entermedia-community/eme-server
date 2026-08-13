#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

sudo docker start ${INSTANCE}
sleep 5
sudo docker logs -f --tail 500 ${INSTANCE}

