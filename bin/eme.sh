#!/bin/bash

# Exit on unexpected errors if needed (uncomment for strictness)
# set -e

CMD="${1:-help}"
SERVERHOME="$2"
SERVERNAME="$(basename "${SERVERHOME:-}")"


echo "*** Running $CMD command"

 # Verify not running as root if CMD is not dockerstart
if [[ "$CMD" != "dockerstart" ]]; then
    if [[ $(id -u) -eq 0 ]]; then
        echo "ERROR: Don't run this script as root directly." >&2
        exit 1
    fi
fi

case "$CMD" in
  developer | init | dockerbuild | dockerstart | update | branchpush)

    # Check if SERVERHOME is set
    if [ -z "$SERVERHOME" ]; then
        echo "ERROR: SERVERHOME is not set. Please provide a server path as the second argument." >&2
        exit 1
    fi

    mkdir -p "$SERVERHOME"
    cd "$SERVERHOME" || exit 1
    SERVERHOME=$(pwd)

    NODENUMBER="${3:-1}"

    # Create .env file if missing
    if [ ! -f "$SERVERHOME/.env" ]; then
        echo "Creating $SERVERHOME/.env file"
        {
          echo "INSTANCE=$SERVERNAME$NODENUMBER"
          echo "SITE=$SERVERNAME"
          echo "NODENUMBER=$NODENUMBER"
          echo "LISTOFPLUGINS=\"catalog|finder|system|manager|mediadb|community|openedit|profile\""
        } > "$SERVERHOME/.env"
    fi
  ;;
esac

case "$CMD" in
  init | developer | dockerbuild)

    USERID="${SUDO_USER:-$(id -un)}"

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

    "$SERVERHOME/bin/plugins.sh"

    ## Copy default site if missing
    if [ ! -d "$SERVERHOME/webapp/site" ]; then
        echo "*** Copying default site to $SERVERHOME/webapp/site"
        cp -rp "$SERVERHOME/webapp/system/templates/webapp/site" "$SERVERHOME/webapp/site"
    fi

  ;;
esac

if [ "$CMD" = "start" ]; then

    #if $SERVERHOME does not exist, exit with error
    if [ ! -d "$SERVERHOME" ]; then
        echo "ERROR: $SERVERHOME does not exist. Run: eme.sh init <server-path>" >&2
        exit 1
    fi

    if [ -z "$JAVA_HOME" ]; then
        if [ -d "$HOME/.sdkman/candidates/java/current" ]; then
            JAVA_HOME="$HOME/.sdkman/candidates/java/current"
        elif [ -d "/usr/lib/jvm/default-java" ]; then
            JAVA_HOME="/usr/lib/jvm/default-java"
        else
            echo "ERROR: JAVA_HOME is not set and fallback JRE paths do not exist." >&2
            echo "Install JDK via SDKMAN: curl -s \"https://get.sdkman.io\" | bash && sdk install java 26.0.1-open" >&2
            exit 1
        fi
    fi

    echo "*** Compiling $SERVERNAME using JAVA_HOME = $JAVA_HOME"

    "$SERVERHOME/bin/compile.sh"

    mkdir -p "$SERVERHOME/tomcat/work"

    # FIXED SYNTAX BUG: [[ ... ]] used for compound condition
    if [[ ! -L "$SERVERHOME/data" || ! -d "$SERVERHOME/webapp/WEB-INF/data" ]]; then
        mkdir -p "$SERVERHOME/webapp/WEB-INF/data"
        ln -nsf "$SERVERHOME/webapp/WEB-INF/data" "$SERVERHOME/data"
        sudo chown -R "$USERID:$GROUPID" "$SERVERHOME/data"
    fi

    if [ ! -d "$SERVERHOME/data/system" ]; then
        cp -rp "$SERVERHOME/plugins/system/defaultdata" "$SERVERHOME/webapp/WEB-INF/data/system"
        sudo chown -R "$USERID:$GROUPID" "$SERVERHOME/webapp/WEB-INF/data/system/"
    fi

    ARGS_TEMPLATE="$SERVERHOME/bin/resources/tomcat.args"

    if [ ! -f "$ARGS_TEMPLATE" ]; then
        echo "ERROR: $ARGS_TEMPLATE not found. Run: eme.sh init <server-path>" >&2
        exit 1
    fi

    echo "*** Starting server: $SERVERHOME"

    EXPANDED_ARGS="$SERVERHOME/tomcat/work/tomcat-args.txt"
    sed -e "s|\$SERVERHOME|$SERVERHOME|g" "$ARGS_TEMPLATE" > "$EXPANDED_ARGS"
    sudo chmod 600 "$EXPANDED_ARGS"

    JAVA="$JAVA_HOME/bin/java"

    echo "$JAVA -Dappname=$SERVERNAME $(cat "$EXPANDED_ARGS") org.apache.catalina.startup.Bootstrap start"
    "$JAVA" -Dappname="$SERVERNAME" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start
fi

case "$CMD" in
  update)
    echo "Updating eme-server-client repo to the latest version"

    git stash
    git pull --no-rebase origin main
    git stash pop
    
    "$SERVERHOME/bin/plugins.sh" pull

   ;;

  branchpush)
    echo "Pushing eme-server-client repo to the remote repository"
    COMMITMESSAGE="${3:-Update from Client}"
   
    git add -A .
    git commit -m "$COMMITMESSAGE" || true
    git pull --no-rebase origin main
    git push origin main

  ;;
  
  updatefork)
    echo "Pulling from upstream"

    if ! git remote | grep -q upstream; then
        git remote add upstream https://github.com/entermedia-community/eme-server.git
    fi
    git fetch upstream
    git merge upstream/main --allow-unrelated-histories -X theirs

  ;;

  developer)
    echo "Opening default workspace in VS Code for development"
    code eme-server.code-workspace

  ;;

  dockerbuild)

    if [ -z "$2" ] || [ -z "$3" ] || [ -z "$4" ]; then
        echo "Usage: eme.sh dockerbuild <server-path> <nodenumber> <ownedby>"
        exit 1
    fi
    
    USERNAME="$4"
    USERID=$(id -u "$USERNAME")
    GROUPID=$(id -g "$USERNAME")

    echo "*** Creating Docker instance for $SERVERHOME with node number $NODENUMBER owned by $USERNAME (UID: $USERID, GID: $GROUPID)"
    
    curl -s https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/bin/resources/docker/scripts/eme-docker-init.sh | bash -s -- "$SERVERHOME" "$NODENUMBER" "$USERID" "$GROUPID"

  ;;

  dockerstart)

    if [[ $EUID -ne 0 ]]; then
       echo "ERROR: This script must be run as root" >&2
       exit 1
    fi

    # Moved USERID validation up
    if [ -z "$USERID" ]; then
       echo "ERROR: USERID environment variable is missing" >&2
       exit 1
    fi

    if [[ ! $(id entermedia 2>/dev/null) ]]; then
        groupadd -g "$GROUPID" "entermedia"
        useradd -m -s /bin/bash -u "$USERID" -g "entermedia" "entermedia"
        echo "entermedia ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/entermedia
        chmod 0440 /etc/sudoers.d/"entermedia"
    fi

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

    trap 'term_handler' SIGTERM

    wait "$launcherpid"
    echo "Launcher process $launcherpid exited"

  ;;

  init | start)
    # Work already done in preflight blocks above.
  ;;

  help|*)
        echo "Eme Server Management Script"
        echo ""
        echo "*Run this script from the command line with a sudoer account. Do not run as root directly."
        echo ""
        echo "General usage:"
        echo "  eme.sh <command> [server-path] [args]"
        echo ""
        echo "Core commands:"
        echo "  init         <server-path>              Prepare local server files without starting server"
        echo "  start        <server-path>              Start Tomcat server"
        echo ""
        echo "Developer commands:"
        echo "  developer    <server-path>                     Clone/setup workspace and open VS Code"
        echo "  update       <server-path>                     Pull latest changes and plugins"
        echo "  updatefork   <server-path>                     Update from upstream remote"
        echo "  branchpush   <server-path> [commit message]    Commit and push local changes"
        echo ""
        echo ""
        echo "Docker commands:"
        echo "  dockerbuild  <server-path> <nodenumber> <ownedby>"
        echo "                                                 Build new Docker instance"
        echo "  dockerstart  <server-path>                     Start inside Docker container"
        echo ""
        exit 0
    ;;
esac