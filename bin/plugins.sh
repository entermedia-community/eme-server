#!/bin/bash

LISTOFPLUGINS="catalog|finder|system|manager|mediadb|community|openedit|profile"


##pull 
##push "Message"
##or no argument to see all


##get the path from the current script
SERVERHOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

IFS='|' read -r -a plugins <<< "$LISTOFPLUGINS"

CMD="$1"

##loop over list of plugins and pull them from github
for plugin in "${plugins[@]}"; do
    cd "$SERVERHOME"
    ## remove submodule if it exists
    
    ##alwys pull if missing
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
        # Switch to main, force tracking, and pull down remote history
        git checkout -B main origin/main
    fi

   cd "$SERVERHOME/plugins/$plugin"


    if [ "$CMD" == "pull" ]; then
        cd "$SERVERHOME/plugins/$plugin"
        if [ -n "$(git status --porcelain)" ]; then
            echo -e "\e[34mplugins/$plugin has uncommitted changes, skipping pull. Run: git fetch --unshallow origin main\e[0m"
            continue
        fi
        echo "Reset to latest changes for $plugin"
        git fetch --depth=1 origin main
        git reset --hard origin/main
    else
    ##look for changes
        if [ -n "$(git status --porcelain)" ]; then
            ##red text for plugin with changes
            echo -e "\e[31mplugins/$plugin has uncommitted changes\e[0m"
            if [ "$CMD" == "push" ]; then
                ##loop over list of plugins and pull them from github
                COMMITMESSAGE="$2"
                if [ -z "$COMMITMESSAGE" ]; then
                    COMMITMESSAGE="Auto commit from pluginsstatus.sh"
                fi
                git add -A .
                git commit -m "$COMMITMESSAGE"
                git pull  --no-rebase  origin main  ##Do a full pull to avoid issues with shallow clones
                git push  origin main
            elif [ "$CMD" == "pull" ]; then
                cd "$SERVERHOME/plugins/$plugin"
                if [ -n "$(git status --porcelain)" ]; then
                    echo -e "\e[34mplugins/$plugin has uncommitted changes, skipping pull. Run: git fetch --unshallow origin main\e[0m"
                    continue
                fi
                echo "Reset to latest changes for $plugin"
                git fetch --depth=1 origin main
                git reset --hard origin/main
            fi
        else
            echo -e "\e[34mplugins/$plugin\e[0m: No changes pending"
        fi
    fi
    git log -1 --format="%an <%ae> - %s"
    cd "$SERVERHOME"
    ##make sure all the symlinks are in place for the webapp
    if [ -d "$SERVERHOME/plugins/$plugin/html" ]; then
        if [ ! -L "$SERVERHOME/webapp/$plugin" ]; then
            ln -nsf "$SERVERHOME/plugins/$plugin/html" "$SERVERHOME/webapp/$plugin"
        fi
    fi
done
