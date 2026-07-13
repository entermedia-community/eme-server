#!/bin/bash

set -a
source ../.env
set +a

sudo bash ../eme-docker-init.sh ${SITE} ${NODENUMBER}
