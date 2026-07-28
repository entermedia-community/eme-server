#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a

sudo bash "$SCRIPT_DIR/../eme-docker-init.sh" ${SITE} ${NODENUMBER}
