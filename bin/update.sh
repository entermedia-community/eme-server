#!/bin/bash

set -a
source ../.env
set +a

sudo docker exec -it -u 0 ${INSTANCE} /usr/bin/eme update /home/entermedia/eme-server
