#!/bin/bash

set -a
source ../.env
set +a
#this is a script to enter the docker container and run bash
sudo docker exec -it ${INSTANCE} bash
