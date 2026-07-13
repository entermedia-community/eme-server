#!/bin/bash

set -a
source ../.env
set +a

sudo docker stop -t 60 $INSTANCE && sudo docker start $INSTANCE
