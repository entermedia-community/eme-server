#!/bin/bash -x

#set -e

##    curl -fsSL get-eme.eme.world | bash -s -- help
   
   
CMD="${1:-help}"
SERVERHOME="$2"
SERVERNAME="$(basename "$SERVERHOME")"

echo "Running $CMD command"

case "$CMD" in

  developer | init | start | dockerbuild | dockerstart | update | updatefork | branchpush)

    mkdir -p "$SERVERHOME"
    cd "$SERVERHOME"
    SERVERHOME=$(pwd)

    echo "INSTANCE=$SERVERNAME$NODENUMBER" > "$SERVERHOME/.env"
    echo "SITE=$SERVERNAME" >> "$SERVERHOME/.env"
    echo "NODENUMBER=$NODENUMBER" >> "$SERVERHOME/.env"
    ##echo "IP_ADDR=$IP_ADDR" >> "$SERVERHOME/.env"

  ;;&

  developer | init | start)

    #verify is not running as root
    if [[ $(id -u) -eq 0 ]]; then
        echo "Don't run this script as root."
        exit 1
    fi

  ;;&

  init | developer | start | dockerbuild)    

    #Set USERID and GROUPID to the current user
    USERID="$(id -un)"

    if GROUPNAME=$(id -gn "$USERID" 2>/dev/null); then
        GROUPID="$GROUPNAME"
    else
        GROUPID="$USERID"
    fi

    sudo chown "$USERID:$GROUPID" "$SERVERHOME"

    if [ ! -d "$SERVERHOME/.git" ]; then
        echo "Cloning eme-server repo into $SERVERHOME"
        git init
        git remote add origin https://github.com/entermedia-community/eme-server.git
        git fetch origin
        git checkout -t origin/main
    fi  
    
    $SERVERHOME/bin/pluginspull.sh

    ;;&

    start)
        ##JAVA_HOME is not set throw an error if JAVA_HOME is not set
        if [ -z "$JAVA_HOME" ]; then
            #checi if there is a jre path
            if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
                JAVA_HOME="$HOME/.sdkman/candidates/java/current"
            elif [ -d "/usr/lib/jvm/default-java" ]; then
                JAVA_HOME="/usr/lib/jvm/default-java"
            else
                echo "JAVA_HOME is not set and /usr/lib/jvm/jre does not exist. Please set JAVA_HOME to a valid JDK path."
                echo "Please check your local java version: curl -s \"https://get.sdkman.io\" | bash && sudo sdk install java 26.0.1-open"
                exit 1
            fi
        fi  

        #Compile the eme-lib if it has not been compiled yet
        $SERVERHOME/bin/compile.sh

        if [ ! -d "$SERVERHOME/tomcat/work" ]; then
            mkdir -p "$SERVERHOME/tomcat/work"
        fi

    #   sudo chown ${USERID}:${GROUPID} "$SERVERHOME/webapp/"
        if [ ! -L "$SERVERHOME/data" ]; then
            mkdir -p "$SERVERHOME/webapp/WEB-INF/data"
            ln -nsf "$SERVERHOME/webapp/WEB-INF/data" "$SERVERHOME/data"
            sudo chown -R $USERID:$GROUPID "$SERVERHOME/data"
        fi

        if [ ! -d "$SERVERHOME/data/system" ]; then
            #mkdir -p "$SERVERHOME/webapp/WEB-INF/data/system/"
            cp -rp "$SERVERHOME/plugins/system/defaultdata" "$SERVERHOME/webapp/WEB-INF/data/system"
            sudo chown -R $USERID:$GROUPID "$SERVERHOME/webapp/WEB-INF/data/system/"
        fi

        ARGS_TEMPLATE="$SERVERHOME/bin/resources/tomcat.args"

            echo "**** Starting $SERVERNAME using JAVA_HOME  = $JAVA_HOME"


            if [ ! -f "$ARGS_TEMPLATE" ]; then
                echo "ERROR: $ARGS_TEMPLATE not found. Run: eme.sh init <server-path>" >&2
                exit 1
            fi
        echo "**** Starting server from: $SERVERHOME"

        # Java @argfile does not expand shell variables, so expand them here
        mkdir -p "$SERVERHOME/tomcat/work"
        EXPANDED_ARGS="$SERVERHOME/tomcat/work/tomcat-args.txt"
    #    trap " rm -f $EXPANDED_ARGS" EXIT
        sed -e "s|\$SERVERHOME|$SERVERHOME|g" "$ARGS_TEMPLATE" > "$EXPANDED_ARGS"
        sudo chmod 600 "$EXPANDED_ARGS"

        JAVA="$JAVA_HOME/bin/java"
    
        #Run Tomcat as entermedia user
        echo "$JAVA -Dappname=$SERVERNAME $(cat "$EXPANDED_ARGS") org.apache.catalina.startup.Bootstrap start"
        "$JAVA" -Dappname="$SERVERNAME" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start

    ;;&

  update | branchpush)
    ## Updates the eme-server-client repo to the latest version
    echo "Updating eme-server-client repo to the latest version"

    git stash
    git pull --no-rebase origin main
    git stash pop
    
   ;;&

  branchpush | updatefork)
    ## Pushes the eme-server-client repo to the remote repository
    echo "Pushing eme-server-client repo to the remote repository"
    COMMITMESSAGE="${3:-Update from Client}"
   
    git add -A .
    git commit -m "$COMMITMESSAGE" || true
    git pull --no-rebase origin main
    git push --no-rebase origin main

  ;;&
  
  updatefork)
    ## Pulls the eme-server-client repo from the upstream repository. We have to make sure all changes are committed and pushed to the origin before we can pull from upstream. This is because we are using rebase to keep the history clean.
    echo "Pulling from upstream"

    ##make sure upstream remote is set
    if ! git remote | grep -q upstream; then
        git remote add upstream https://github.com/entermedia-community/eme-server.git
    fi
    git fetch upstream
    git merge upstream/main

  ;;

    developer)
    ## Opens default workspace in VS Code for development
    echo "Opening default workspace in VS Code for development"
    code eme-server.code-workspace

  ;;

      dockerbuild)

    echo "Creating Docker instance for $SERVERHOME"

    ##this is run outside the docker container to create a new instance of the eme server

    ##if missing $2, $3 or $4 exit
    if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
        echo "Usage: eme.sh dockerbuild <server-path> <nodenumber> <ownedby>"
        exit 1
    fi

    NODENUMBER="$3"
    USERNAME="$4"
    if [ -z "$USERNAME" ]; then
       echo "USERNAME not set using running user as default"
       USERNAME=$(whoami)
    fi
    USERID=$(id -u "$USERNAME")
    GROUPID=$(id -g "$USERNAME")
    
    curl -s https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/bin/resources/docker/scripts/eme-docker-init.sh | sudo bash -s -- "$SERVERHOME" "$NODENUMBER" "$USERID" "$GROUPID"
  ;;

  dockerstart)

    #this must be started as root
    if [[ $EUID -ne 0 ]]; then
       echo "This script must be run as root"
       exit 1
    fi

   #This is run from inside the docker container to start the server
    if [ -z "$USERID" ]; then
       echo "USERID should have been passed in as an argument to the docker container"
         exit 1
    fi

    #make sure entermedia user is setup to match the id passed in from the host machine
    if [[ ! $(id entermedia 2>/dev/null) ]]; then
        groupadd -g "$GROUPID" "entermedia"
        useradd -m -s /bin/bash -u "$USERID" -g "entermedia" "entermedia"
        echo "entermedia ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/entermedia
        chmod 0440 /etc/sudoers.d/"entermedia"
    fi

    #Verify if $USERID is passed in
    if [ -z "$USERID" ]; then
       echo "USERID not set"
       exit 1 
    fi

    ##move this to the docker build script or mount /etc/resolv.conf to the host machine
    echo "Starting Tomcat in foreground (PID: $$)"

    sudo -u "entermedia" /usr/bin/eme start "$2" &
    launcherpid=$!

    catalinapid=""
    for _ in $(seq 1 50); do
        catalinapid=$(pgrep -n -f "eme start" || true)
        if [[ "$catalinapid" =~ ^[0-9]+$ ]]; then
            echo "Tomcat PID: $catalinapid (launcher PID: $launcherpid)"
            break
        fi
        sleep 0.3
    done
    if [[ ! "$catalinapid" =~ ^[0-9]+$ ]]; then
        echo "Could not find eme start process"
        exit 1
    fi

   # SIGTERM handler
   term_handler() {
        trap - SIGTERM
       echo "SIGTERM received, shutting down Tomcat (PID: ${catalinapid:-unset}, launcher PID: ${launcherpid:-unset})"

        if [[ "$catalinapid" =~ ^[0-9]+$ ]] && kill -0 "$catalinapid" 2>/dev/null; then
            echo "Deployment shutdown start"
            "$SERVERHOME/tomcat/bin/catalina.sh" stop || true
            kill -TERM "$catalinapid" 2>/dev/null || true

            while kill -0 "$catalinapid" 2>/dev/null; do
                printf "."
                sleep 0.6
            done
        fi

        if [[ "$launcherpid" =~ ^[0-9]+$ ]]; then
            kill -TERM "$launcherpid" 2>/dev/null || true
        fi

        echo
        echo "Tomcat shutdown complete, exiting (143)"
        exit 143
    }
    #Send SIGTERM to the PID of the most recently started background job
    #trap 'kill $$; term_handler' SIGTERM
    trap 'term_handler' SIGTERM

    wait "$launcherpid"
    echo "Launcher process $launcherpid exited"

    ;;


  help)
        echo -e "Eme Server Management Script\n"
        echo "General usage:"
        echo "  eme.sh <command> [server-path] [args]"
        echo "Core commands:"
        echo "  init         <server-path>                     Prepare local server files but dont start server"
        echo "  start        <server-path>                     Start tomcat server on port 8080"
        echo
        echo "Developer commands:"
        echo "  developer    <server-path>                     Clone/setup and open VSS workspace"
        echo "  update       <server-path>                     Pull submodules and local updates"
        echo "  updatefork    <server-path>                     Update, push, and rebase upstream"
        echo "  branchpush   <server-path> [commit message]    Commit/pull/push changes"
        echo
        echo "Docker commands:"
        echo "  dockerbuild <server-path> <nodenumber> <ownedby>"
        echo "                                                 Create new docker instance or rebuilds existing instance"
        echo "  dockerstart  <server-path>                     Start inside docker container"
        echo
        echo "Examples:"
        echo "  bash eme.sh developer /opt/eme-server"
        echo "  sudo bash eme.sh dockerbuild /opt/eme-server 1 myuser"
        echo "  eme.sh start /opt/eme-server"
    exit 0
    ;;
esac
