#!/bin/bash -e

set -x 

##This is run from the /bin/eme location that is linked

CMD="${1:-start}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ "$CMD" = "version" ]; then
    echo "eme-lib version: 0.1.0"
    exit 0
fi


# Resolve EMELIB: prefer sibling eme-lib, then env var, then system default

function get_relative_emelib {
    local levels=$1
    local relative_path=""      
    for ((i=0; i<levels; i++)); do
        relative_path="../$relative_path"
    done
    relative_path="${relative_path}eme-lib"
    echo "$relative_path"
}

EMELIB="/"

if [ -d "$EMELIB" ]; then
    export EMELIB
else
    echo "ERROR: Cannot find eme-lib. $EMELIB" >&2
  #  exit 1
fi


##JAVA_HOME is not set throw an error if JAVA_HOME is not set
if [ -z "$JAVA_HOME" ]; then
    #checi if there is a jre path
    if [ -d "/usr/lib/jvm/jre" ]; then
        JAVA_HOME="/usr/lib/jvm/jre"
    elif [ -d "/usr/lib/jvm/default-java" ]; then
        JAVA_HOME="/usr/lib/jvm/default-java"
    else
        echo "JAVA_HOME is not set and /usr/lib/jvm/jre does not exist. Please set JAVA_HOME to a valid JRE path."
        echo "Please run iwth your local java version: sudo update-alternatives --install /usr/lib/jvm/jre jre /usr/lib/jvm/java-X.XX.X-openjdk-amd64 20000"
        exit 1
    fi
fi  

case "$CMD" in
  dockerstart)
    #Verify if $USERID is passed in
    if [ -z "$USERID" ]; then
       echo "USERID not set"
       exit 1 
    fi
    #make sure $USERID doees not already exist as a user, if not create entermedia user with $USERID and $GROUPID

    if [[ ! $(id $USERID 2>/dev/null) ]]; then
        groupadd -g $GROUPID entermedia
        useradd -ms /bin/bash entermedia -g entermedia -u $USERID
        echo "entermedia ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/entermedia
        chmod 0440 /etc/sudoers.d/entermedia
    fi

    JAVA_HOME="/usr/lib/jvm/java-18-openjdk-amd64"
    export JAVA_HOME

    sudo -u entermedia /usr/bin/eme start "$2"
    ;;

  init | start)    
    if [[ $(id -u) -eq 0 ]]; then
        echo "Don't run this script as root."
        exit 1
    fi
    
    #Set USERID and GROUPID to the current user if not running in Docker
    USERID="$(id -un)"
    if GROUPNAME=$(id -gn "$USERID" 2>/dev/null); then
        GROUPID="$GROUPNAME"
    else
        GROUPID="$USERID"
    fi

    SERVERHOME="$2"

    echo "**** Starting server from: $SERVERHOME"

    if [ ! -d "$SERVERHOME/webapp/_site.xconf" ]; then
        #get parent directory of SERVERHOME
        PARENTDIR="$(dirname "$SERVERHOME")"
        sudo mkdir -p "$PARENTDIR"
        sudo chown "$USERID:$GROUPID" "$PARENTDIR"
        cd "$PARENTDIR"

        git clone -b main --depth 1  https://github.com/entermedia-community/eme-server.git $SERVERHOME
        cd "$SERVERHOME"
        git remote add upstream https://github.com/entermedia-community/eme-server.git
        git submodule update --init --recursive --depth 1
        #git fetch upstream 
        #git merge upstream/main
    fi    
    #check ownership of target, if not owned by current user, change ownership to current user
    #if [ "$(stat -c '%u:%g' "$SERVERHOME")" != "$USERID:$GROUPID" ]; then
    #    echo "Changing ownership of $SERVERHOME to $USERID:$GROUPID"
    #    sudo chown "$USERID:$GROUPID" "$SERVERHOME"
    #fi  

    cd "$SERVERHOME"

    #Compile the eme-lib if it has not been compiled yet
    $SERVERHOME/plugins/system/resources/bin/compile.sh
    
    #$USER is the user running the container

    if [ ! -d "$SERVERHOME/tomcat" ]; then
        # Copy tomcat conf and webapp templates from eme-lib deploy
        # Create directory structure
        mkdir -p "$SERVERHOME/tomcat" "$SERVERHOME/tomcat/conf" "$SERVERHOME/tomcat/logs" "$SERVERHOME/tomcat/webapps" "$SERVERHOME/tomcat/work"
        cp -rn "$EMELIB/tomcat/conf/." "$SERVERHOME/tomcat/conf/" 2>/dev/null || true
        cp -rpn "$EMELIB/tomcat/bin" "$SERVERHOME/tomcat/" 2>/dev/null || true
        echo "export CATALINA_BASE=\"$SERVERHOME/tomcat\"" >>"$SERVERHOME/tomcat/bin/setenv.sh"
        sudo chown -R $USERID:$GROUPID "$SERVERHOME/tomcat" 
        #chmod 755 "$SERVERHOME/tomcat/bin/*.sh"
    fi


    #if [ ! -L "$SERVERHOME/webapp/_site.xconf" ]; then
    #    mkdir -p "$SERVERHOME/webapp/WEB-INF/"
    #    ln -nsf "$(get_relative_emelib 2)/resources/webapp/_site.xconf" "$SERVERHOME/webapp/_site.xconf"
    #    sudo chown -R $USERID:$GROUPID "$SERVERHOME/webapp"
    #fi

    if [ ! -f "$SERVERHOME/webapp/WEB-INF/web.xml" ]; then
          cp -rp "$EMELIB/resources/webapp/WEB-INF/web.xml" "$SERVERHOME/webapp/WEB-INF/web.xml"
    fi

    if [ ! -f "$SERVERHOME/webapp/WEB-INF/node.xml" ]; then
          cp -rp "$EMELIB/resources/webapp/WEB-INF/node.xml" "$SERVERHOME/webapp/WEB-INF/node.xml"
    fi

   # if [ ! -L "$SERVERHOME/webapp/WEB-INF/bin" ]; then
   #     ln -nsf "$(get_relative_emelib 3)/resources/webapp/WEB-INF/bin" "$SERVERHOME/webapp/WEB-INF/bin"
   # fi

 #   sudo chown ${USERID}:${GROUPID} "$SERVERHOME/webapp/"
    if [ ! -L "$SERVERHOME/data" ]; then
        mkdir -p "./webapp/WEB-INF/data"
        ln -nsf "./webapp/WEB-INF/data" "./data" 
        sudo chown -R $USERID:$GROUPID "$SERVERHOME/data"
    fi

    if [ ! -d "$SERVERHOME/data/system" ]; then
         #mkdir -p "$SERVERHOME/webapp/WEB-INF/data/system/"
         cp -rp "$EMELIB/plugins/system/defaultdata" "$SERVERHOME/webapp/WEB-INF/data/system"
         sudo chown -R $USERID:$GROUPID "$SERVERHOME/webapp/WEB-INF/data/system/"
    fi

    # symbolically link users plugins to webapp first!
    for plugin in "$SERVERHOME/plugins"/*/; do
        pluginname="$(basename "$plugin")"
        if [ -d "${plugin}html" ]; then
            if [ ! -L "../webapp/$pluginname" ]; then
                ln -nsf "../plugins/${pluginname}/html" "./webapp/$pluginname"
            fi
        fi
    done

    # symbolically link built-in plugins from emelib to webapp, but only if they don't already exist in the target plugins directory (i.e. user overwrote them)
    #only do this if the emelib is relative

    #for plugin in "$(get_relative_emelib 1)/plugins"/*/; do
    #    pluginname="$(basename "$plugin")"

    #    if [ -d "${plugin}html" ]; then
    #        ##if its an invalid symbolic link then remove it and create a new one
    #        echo "Adding plugin: $SERVERHOME/webapp/$pluginname"
    #        if [ -L "$SERVERHOME/webapp/$pluginname" ]; then
    #            echo "Removing invalid symbolic link: $SERVERHOME/webapp/$pluginname"
    #            rm "$SERVERHOME/webapp/$pluginname"
    #        fi
    #        ln -nsf "$(get_relative_emelib 2)/plugins/${pluginname}/html"  "$SERVERHOME/webapp/$pluginname"
    #    fi
    #done

    export EMSERVER="${2:-$SCRIPT_DIR}"
    export EMSERVER="$(cd "$EMSERVER" && pwd)"
    export EMSERVER_NAME="$(basename "$EMSERVER")"

    # Write VSCode configs

    mkdir -p "$SERVERHOME/.vscode"
    sed -e "s|\$JAVA_HOME|$JAVA_HOME|g" "$SERVERHOME/plugins/system/resources/editor-configs/settings.json" > "$SERVERHOME/.vscode/settings.json"

    sed -e "s|\$EMSERVER_NAME|$EMSERVER_NAME|g" "$SERVERHOME/plugins/system/resources/editor-configs/launch.json" > "$SERVERHOME/.vscode/launch.json"
    printf "// Do not edit this file unless you know what you are doing\n" | cat - "$SERVERHOME/.vscode/launch.json" > "$SERVERHOME/.vscode/temp" && mv "$SERVERHOME/.vscode/temp" "$SERVERHOME/.vscode/launch.json"

    cp "$SERVERHOME/plugins/system/resources/editor-configs/formatter.xml" "$SERVERHOME/formatter.xml"

    sed -e "s|\$EMELIB|$EMELIB|g" -e "s|\$EMSERVER|$EMSERVER|g" "$SERVERHOME/plugins/system/resources/editor-configs/eme.code-workspace" > "$SERVERHOME/$EMSERVER_NAME.code-workspace"

    if command -v code >/dev/null 2>&1; then
        code "$SERVERHOME/$EMSERVER_NAME.code-workspace" && echo "Launching VS Code, press F5 to start your server" && exit 0
    else
        echo "VS Code 'code' command not found. Open $SERVERHOME/$EMSERVER_NAME.code-workspace manually."
    fi

    ARGS_TEMPLATE="$SERVERHOME/plugins/system/resources/bin/tomcat.args"

    echo "**** Starting $EMSERVER_NAME using JAVA_HOME  = $JAVA_HOME"


    if [ ! -f "$ARGS_TEMPLATE" ]; then
        echo "ERROR: $ARGS_TEMPLATE not found. Run: eme.sh init <server-path>" >&2
        exit 1
    fi

    if( $CMD = "init" ); then
        echo "Initialization complete. Run: eme.sh start <server-path> to start the server."
        exit 0
    fi  

    # Java @argfile does not expand shell variables, so expand them here
    mkdir -p "$SERVERHOME/tomcat/work"
    EXPANDED_ARGS=$( mktemp $SERVERHOME/tomcat/work/tomcat-args.XXXXXX)
    sudo chmod 600 "$EXPANDED_ARGS"
    trap " rm -f $EXPANDED_ARGS" EXIT
    sed -e "s|\$SERVERHOME|$SERVERHOME|g" "$ARGS_TEMPLATE" > "$EXPANDED_ARGS"

    JAVA="$JAVA_HOME/bin/java"

    echo "$JAVA $(cat "$EXPANDED_ARGS") org.apache.catalina.startup.Bootstrap start"

    #Run Tomcat as entermedia user
    #  "$JAVA" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start 

    
    CATALINA_BASE="$SERVERHOME/tomcat"
    export CATALINA_BASE
    #SIGTERM-handler
    term_handler() {
        
        pid=$(pgrep -f "$CATALINA_BASE/conf/logging.properties")
        echo "SIGTERM received, shutting down Tomcat (PID: $pid)"
        if [[ ! -z $pid ]]; then
            if [ $pid -ne 0 ]; then
                echo "Deployment shutdown start"
                 sh -c "$SERVERHOME/tomcat/bin/catalina.sh stop"
                kill -SIGTERM "$catalinapid"
                while [ -e /proc/$pid ]; do
                    printf .
                    sleep 1
                done
            fi
        fi
        echo "Tomcat shutdown complete, exiting (143)"
        exit 143 # 128 + 15 -- SIGTERM
    }

    #SIGKILL
    # setup handlers
    # on callback, kill the last background process, which is `tail -f /dev/null` and execute the specified handler
    trap 'kill ${!}; term_handler' SIGTERM

    #Run application
    # sh -c "$EMTARGET/tomcat/bin/catalina.sh run" &

    "$JAVA" "@$EXPANDED_ARGS" org.apache.catalina.startup.Bootstrap start 
    
    catalinapid=0
    while [ $catalinapid -eq 0 ]; do
        catalinapid=$(pgrep -f "eme start")
        ##make sure its an integer not a string
        catalinapid=$(echo "$catalinapid" | awk '{print int($0)}')
        echo "Catalina PID: $catalinapid"
        sleep 1
    done

    wait $catalinapid
    echo "Tomcat process $catalinapid exited"
    ;;

  *)
        echo "Usage: eme.sh [version | dockerstart <server-path> | start [server-path]]" >&2
    exit 1
    ;;
esac
