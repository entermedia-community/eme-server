#!/bin/bash

#####################################
#
# Launch EnterMediadb using entermediadb/entermedia:latest Docker image
#
#####################################

set -eo pipefail

if [ -z $BASH ]; then
  echo Using Bash...
  exec "/bin/bash" $0 $@
  exit
fi

# Root check
if [[ ! $(id -u) -eq 0 ]]; then
  echo "You must run this script as the superuser. Usage: sudo ./eme-docker-init.sh serverpath nodenumber"
  exit 1
fi

if [ "$#" -ne 4 ]; then
    echo "Usage: sudo ./eme-docker-init.sh <serverpath> <nodenumber> <userid> <groupid>"
    exit 1
fi

# Setup
DOCKERPROJECT=entermediadb
DOCKERIMAGE=eme-server
BRANCH=latest
DOCKERNETWORKBASE=172.18.0
SERVERHOME=$1
SERVERNAME="$(basename "$SERVERHOME")"
NODENUMBER=$2
USERID=$3
GROUPID=$4

if [ "$NODENUMBER" -lt 1 ] || [ "$NODENUMBER" -gt 250 ]; then
    echo "Node Number must be between 1-250" ; exit 1
else 
    echo "Using Node Number: $NODENUMBER"
fi

INSTANCE=$SERVERNAME$NODENUMBER
DOCKERNETWORK=entermedia

# Pull latest images
docker pull $DOCKERPROJECT/$DOCKERIMAGE:$BRANCH

ALREADY=$(docker ps -aq --filter name=$INSTANCE)
[[ $ALREADY ]] && docker stop -t 60 $ALREADY && docker rm -f $ALREADY

IP_ADDR="$DOCKERNETWORKBASE.$NODENUMBER"
# Create entermedia user if needed

# Docker networking
if [[ ! $(docker network ls | grep $DOCKERNETWORK) ]]; then
  docker network create --subnet $DOCKERNETWORKBASE.0/16 $DOCKERNETWORK
fi

# TODO: support upgrading, start, stop and removing

# Create custom scripts
SCRIPTROOT=${SERVERHOME}/bin

#echo "Review the following URL to get the full TZ list"
#echo "https://en.wikipedia.org/wiki/List_of_tz_database_time_zones"
echo "Default time zone(TZ) set to US Eastern time"

if [ ! -f "$SERVERHOME/.env" ]; then	
    echo "Requires an .env file in $SERVERHOME"
	exit 1
fi

set -e
# Run Create Docker Instance, add Mounted HotFolders as needed
docker run -t -d \
	--restart unless-stopped \
	--net $DOCKERNETWORK \
	`#-p 22$NODENUMBER:22` \
	--ip $IP_ADDR \
	--name $INSTANCE \
	--dns 1.1.1.1 --dns 8.8.8.8 \
	--log-opt max-size=10m --log-opt max-file=10 \
	--cap-add=SYS_PTRACE \
	-e TZ="America/New_York" \
	-e USERID=$USERID \
	-e GROUPID=$GROUPID \
	-e CLIENT_NAME=$SERVERNAME \
	-e INSTANCE_PORT=$NODENUMBER \
	-v ${SERVERHOME}/:/home/entermedia/eme-server \
	$DOCKERPROJECT/$DOCKERIMAGE:$BRANCH \
	/usr/bin/eme dockerstart /home/entermedia/eme-server

	#/usr/bin/bash

echo ""
echo "Node is running: curl http://$IP_ADDR:8080 from $SERVERHOME"
echo ""
echo "${SCRIPTROOT}/logs.sh to view logs"
 