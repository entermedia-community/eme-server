
# eme-server

Instructions to run a single eme-server, fork and init a new eme-server-client and instructions for setup Develoment environments to work with eme-server and eme-plugins.

EME uses a deploy from GIT architecture. This allows for rapid customization using GIT to deploy from your local development environment directly into a testing or production. 

    ```

    curl -fsSL get-eme.eme.world | bash -s -- help
    
    ##This will clone the eme repo. To save your changes you should fork the local repo and push your own git repository

    curl -L get-eme.eme.world | bash -s -- developer $HOME/git/MyEmEServer

    curl -L get-eme.eme.world | bash -s -- dockerbuild $HOME/git/MyEmEServer 20 entermedia

    ```

## AI Developer Setup

### Work in our own Branch

    ##Each project should have a project in a GIT repository. This way AI can customize the code and you can keep track of the changes made by AI. 



### Make Pull Requests
In case you want to contribute changes to eme-server, cherry pick changes to your *toupstream* branch and start a pull request procedure in github.

### Add SubModules

1. Add submodule with specific destination path
    ```
    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme_plugin_app.git plugins/app


##These are already built in to the base repository

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-community.git plugins/community

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-system.git plugins/system

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-profile.git plugins/profile

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-catalog.git plugins/catalog

    ```
2. Init and update submodules
    ```
    git submodule update --init --recursive --depth 1
    ```
3. Checkin changes
    ```
    git add * && git commit -m "Plugins Added" && git push
    ```

### Deleting Submodules

1. Deinit submodules
    ```
    git submodule deinit -f plugins/eme-lib
    ```
2. Remove folders with git
    ```
    git rm --cached -r plugins/eme-lib
    ```
3. Manually delete Plugin entry in .gitmodules

## Custom eme-server-client instance install

1. Install a base eme-server Docker instance (Instructions in top)

2. ```
    cd eme-server-myserver
    git remote set-url origin https://github.com/entermedia-community/eme-server-myserver.git
    git fetch
    *Resolve conflicts, may need to add useremail/username 
    git pull origin main
    ```


## Usefull git configuration
git config --global pull.ff only
git rebase upstream/main


2. Launch Development Tools
    ```
    curl -L get-eme.eme.world | bash -s -- developer $HOME/git/MyEmEServer
    ```

4. Update the base code from time to time
    ```
    git fetch upstream
    git merge upstream/main
    git submodule update --init --recursive --depth 1
    ```
6. For pull eme-server changes use git rebase
    ```
    *Be sure to have pull fast-forward config properly
    git config pull.rebase true
    git config --global pull.ff only

    *Then pull changes from upstream and rebase
    git fetch upstream
    git rebase upstream/main
    ```


### Make Pull Requests
In case you want to contribute changes to eme-server, cherry pick changes to your *toupstream* branch and start a pull request procedure in github.

### Add SubModules

1. Add submodule with specific destination path
    ```
    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-community.git plugins/community

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-system.git plugins/system

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-profile.git plugins/profile

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme_plugin_app.git plugins/app
    ```
2. Init and update submodules
    ```
    git submodule update --init --recursive --depth 1
    ```
3. Checkin changes
    ```
    git add * && git commit -m "Plugins Added" && git push
    ```

### Deleting Submodules

1. Deinit submodules
    ```
    git submodule deinit -f plugins/eme-lib
    ```
2. Remove folders with git
    ```
    git rm --cached -r plugins/eme-lib
    ```
3. Manually delete Plugin entry in .gitmodules

## Custom eme-server-client instance install

1. Install a base eme-server Docker instance (Instructions in top)

2. ```
    cd eme-server-myserver
    git remote set-url origin https://github.com/entermedia-community/eme-server-myserver.git
    git fetch
    *Resolve conflicts, may need to add useremail/username 
    git pull origin main
    ```


## Usefull git configuration
git config --global pull.ff only
git rebase upstream/main

git fetch upstream
git stash
git rebase upstream/main
git stash pop
git submodule foreach 'git fetch origin'
git submodule foreach 'git checkout main'
git submodule foreach 'git pull origin main'