#!/bin/bash

set -a
source ../.env
set +a

sudo docker exec -it ${INSTANCE} bash
