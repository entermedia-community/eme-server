#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

sudo docker exec -it -u entermedia ${INSTANCE} /usr/bin/eme update /home/entermedia/eme-server
