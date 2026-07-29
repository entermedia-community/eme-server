#!/bin/bash

LISTOFPLUGINS="catalog|finder|system|manager|mediadb|community|openedit|profile"

SERVERHOME="$(pwd)"

IFS='|' read -r -a plugins <<< "$LISTOFPLUGINS"

##bin/pluginsstatus.sh push "commit message"

for plugin in "${plugins[@]}"; do
    cd "$SERVERHOME/plugins/$plugin"
    if [ -n "$(git status --porcelain)" ]; then
        echo -e "\e[34mplugins/$plugin\e[0m"
        if [ "$1" == "push" ]; then
            ##loop over list of plugins and pull them from github
            COMMITMESSAGE="$2"
            git add -A .
            git commit -m "$COMMITMESSAGE"
            git pull origin main
            git push origin main
        else
            git status
        fi
    fi
done
