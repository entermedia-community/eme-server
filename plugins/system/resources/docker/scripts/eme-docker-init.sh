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
  echo "You must run this script as the superuser. Usage: sudo ./eme-docker-init.sh sitename nodenumber"
  exit 1
fi

if [ "$#" -ne 2 ]; then
    echo "Usage: sudo ./eme-docker-init.sh sitename nodenumber"
    exit 1
fi

# Setup
DOCKERPROJECT=entermediadb
DOCKERIMAGE=eme-server
BRANCH=latest
DOCKERNETWORKBASE=172.18.0
SERVERHOMEBASE=/media/emsites
SITE=$1
NODENUMBER=$2

if [ ${#NODENUMBER} -ge 4 ]; then echo "Node Number must be between 100-250" ; exit
else echo "Using Node Number: $NODENUMBER"
fi

INSTANCE=$SITE$NODENUMBER
DOCKERNETWORK=entermedia

# Pull latest images
docker pull $DOCKERPROJECT/$DOCKERIMAGE:$BRANCH

ALREADY=$(docker ps -aq --filter name=$INSTANCE)
[[ $ALREADY ]] && docker stop -t 60 $ALREADY && docker rm -f $ALREADY

IP_ADDR="$DOCKERNETWORKBASE.$NODENUMBER"

SERVERHOME=$SERVERHOMEBASE/$SITE

# Create entermedia user if needed
if [[ ! $(id -u entermedia 2> /dev/null) ]]; then
  groupadd entermedia > /dev/null
  useradd -g entermedia entermedia > /dev/null
fi
USERID=$(id -u entermedia)
GROUPID=$(id -g entermedia)

# Docker networking
if [[ ! $(docker network ls | grep $DOCKERNETWORK) ]]; then
  docker network create --subnet $DOCKERNETWORKBASE.0/16 $DOCKERNETWORK
fi

# TODO: support upgrading, start, stop and removing

# Create custom scripts
SCRIPTROOT=${SERVERHOME}/bin


echo "Review the following URL to get the full TZ list"
echo "https://en.wikipedia.org/wiki/List_of_tz_database_time_zones"
echo "Default time zone(TZ) will be US Eastern time"

#check if .env file exists
if [ -f "$SERVERHOME/.env" ]; then	
	echo "Using existing .env file"
else
	echo "Creating new .env file"
	mkdir -p "$SERVERHOME"
	echo "INSTANCE=$INSTANCE" > "$SERVERHOME/.env"
	echo "SCRIPTROOT=$SCRIPTROOT" >> "$SERVERHOME/.env"
	echo "SITE=$SITE" >> "$SERVERHOME/.env"
	echo "NODENUMBER=$NODENUMBER" >> "$SERVERHOME/.env"
	echo "IP_ADDR=$IP_ADDR" >> "$SERVERHOME/.env"
fi


set -e
# Run Create Docker Instance, add Mounted HotFolders as needed
docker run -t -d \
	--restart unless-stopped \
	--net $DOCKERNETWORK \
	`#-p 22$NODENUMBER:22` \
	--ip $IP_ADDR \
	--name $INSTANCE \
	--log-opt max-size=10m --log-opt max-file=10 \
	--cap-add=SYS_PTRACE \
	-e TZ="America/New_York" \
	-e USERID=$USERID \
	-e GROUPID=$GROUPID \
	-e CLIENT_NAME=$SITE \
	-e INSTANCE_PORT=$NODENUMBER \
	-v ${SERVERHOME}/:/home/entermedia/eme-server \
	$DOCKERPROJECT/$DOCKERIMAGE:$BRANCH \
	/usr/bin/eme dockerstart /home/entermedia/eme-server

	#/usr/bin/bash

# Fix /etc/resolv.conf to independently reflect Cloudflare and Google DNS

docker exec -d $INSTANCE sudo sh -c "truncate -s 0 /etc/resolv.conf"
docker exec -d $INSTANCE sudo sh -c "echo 'nameserver 1.1.1.1' >>/etc/resolv.conf"
docker exec -d $INSTANCE sudo sh -c "echo 'nameserver 8.8.8.8' >>/etc/resolv.conf"
docker exec -d $INSTANCE sudo sh -c "echo 'options ndots:0' >>/etc/resolv.conf"

echo ""
echo "Node is running: curl http://$IP_ADDR:8080 in $SCRIPTROOT"
echo ""
echo "- Run ${SCRIPTROOT}/logs.sh to view logs"
 