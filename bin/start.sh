#!/bin/bash

set -a
source ../.env
set +a

sudo docker start ${INSTANCE}
