#!/bin/bash

if [ -f ".env" ]; then
    export $(grep -v '^#' .env | xargs)
fi

if [ -z "$LISTOFPLUGINS" ]; then
    echo "ERROR: LISTOFPLUGINS is not set. Please set it in the .env file." >&2
    exit 1
fi


##pull 
##push "Message"
##or no argument to see all


##get the path from the current script
SERVERHOME="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

IFS='|' read -r -a plugins <<< "$LISTOFPLUGINS"

CMD="$1"

safe_fetch_main() {
    if [ "$(git rev-parse --is-shallow-repository 2>/dev/null)" = "true" ]; then
        git fetch --unshallow origin main
    else
        git fetch origin main
    fi
}

if [ -n "$CMD" ]; then
    echo "*** Running $CMD command for plugins: ${plugins[*]}"
fi

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


    if [ "$CMD" == "update" ]; then
        cd "$SERVERHOME/plugins/$plugin"
        if [ -n "$(git status --porcelain)" ]; then
            echo -e "\e[34mplugins/$plugin has uncommitted changes, skipping update. Run: git fetch origin main\e[0m"
            continue
        fi  

        git fetch --depth=1 origin main
        # Get total unpushed commit count across all local branches
        UNPUSHED_ALL=$(git log --branches --not --remotes --oneline | wc -l)

        if [ "$UNPUSHED_ALL" -gt 0 ]; then
            echo -e "\e[34mplugins/$plugin\e[0m: You have $UNPUSHED_ALL unpushed commit(s) across your local branches."
        else
            echo " All local branches are fully pushed."
            git reset --hard origin/main
        fi
    elif [ "$CMD" == "pull" ]; then
        cd "$SERVERHOME/plugins/$plugin"
        if [ -n "$(git status --porcelain)" ]; then
            echo -e "\e[34mplugins/$plugin has uncommitted changes, skipping pull. Run: git fetch origin main\e[0m"
            continue
        fi  
        
        echo -e "\e[34mplugins/$plugin\e[0m: Pulling latest changes from origin/main"
        
        safe_fetch_main
        git merge --ff-only origin/main

    elif [ "$CMD" == "push" ]; then
        
        if [ -n "$(git status --porcelain)" ]; then
            ##red text for plugin with changes
            echo -e "\e[31mplugins/$plugin has uncommitted changes\e[0m"
            ##loop over list of plugins and pull them from github
            COMMITMESSAGE="$2"
            if [ -z "$COMMITMESSAGE" ]; then
                COMMITMESSAGE="Auto commit from plugins.sh"
            fi
            git add -A .
            git commit -m "$COMMITMESSAGE"
        fi
        echo -e "\e[34mplugins/$plugin\e[0m: Pulling & Pushing latest changes"
        safe_fetch_main
        git merge --ff-only origin/main
        git push  origin main
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
