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
        git fetch --depth=1 origin main
        git checkout -t origin/main
    fi
    cd "$SERVERHOME/plugins/$plugin"
    if [ -n "$(git status --porcelain)" ]; then
        echo "\e[34mplugins/$plugin has uncommitted changes, skipping pull. Run: git fetch --unshallow origin main\e[0m"
        continue
    fi
    echo "Pulling latest changes for $plugin"
    git fetch --depth=1 origin main
    git reset --hard origin/main

    if [ -d "$SERVERHOME/plugins/$plugin/html" ]; then
        if [ ! -L "$SERVERHOME/webapp/$plugin" ]; then
            ln -nsf "$SERVERHOME/plugins/$plugin/html" "$SERVERHOME/webapp/$plugin"
        fi
    fi


done

