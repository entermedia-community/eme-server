#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a
#this script is to follow the logs of the docker container
sudo docker logs -f --tail 500 ${INSTANCE}
