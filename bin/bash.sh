#!/bin/bash

set -a
SCRIPT_DIR=$(dirname "$0")
source "$SCRIPT_DIR/../.env"
set +a
#this is a script to enter the docker container and run bash
sudo docker exec -it ${INSTANCE} bash
