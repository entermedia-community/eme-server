#!/bin/bash

set -a
source ../.env
set +a

sudo docker logs -f --tail 500 ${INSTANCE}
