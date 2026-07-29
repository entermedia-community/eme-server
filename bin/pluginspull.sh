#!/bin/bash

LISTOFPLUGINS="catalog|finder|system|manager|mediadb|community|openedit|profile"

SERVERHOME="$(pwd)"

IFS='|' read -r -a plugins <<< "$LISTOFPLUGINS"

##loop over list of plugins and pull them from github
for plugin in "${plugins[@]}"; do
    cd "$SERVERHOME"
    ## remove submodule if it exists
    
    if [ ! -d "$SERVERHOME/plugins/$plugin/.git" ]; then        
        if ! grep -qxF "plugins/$plugin/" "$SERVERHOME/.gitignore" 2>/dev/null; then
            echo "Ignore plugin $plugin"
            echo "plugins/$plugin/" >> "$SERVERHOME/.gitignore"
        fi
        mkdir -p "$SERVERHOME/plugins/$plugin"
        cd "$SERVERHOME/plugins/$plugin"
        echo "Cloning $plugin repo into $SERVERHOME/plugins/$plugin"
        git init 
        git remote add origin "https://github.com/entermedia-community/eme-plugin-$plugin.git"
        git branch --set-upstream-to=origin/main main
        git pull origin main --depth=1
    fi
    git pull origin main --depth=1
    if [ -d "$SERVERHOME/plugins/$plugin/html" ]; then
        if [ ! -L "$SERVERHOME/webapp/$plugin" ]; then
            ln -nsf "$SERVERHOME/plugins/$plugin/html" "$SERVERHOME/webapp/$plugin"
        fi
    fi


done

