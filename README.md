
# eme-server

Instructions to run a single eme-server, fork and init a new eme-server-client and instructions for setup Develoment environments to work with eme-server and eme-plugins.

## Developer Intallation

1. Fork server to make your changes

    ##### Save your changes to github by forking our base eme-server project
    ```
    git clone https://github.com/entermedia-community/eme-server.git MyEmEServer
    cd MyEmEServer
    git remote add upstream https://github.com/entermedia-community/eme-server.git
    git config --global init.defaultBranch main
    git submodule update --init --recursive --depth 1 --remote

    ##make some local changes that you dont want to commit yet

    ##Upgrade the base code to see latest changes
    git fetch upstream
    git stash
    git rebase upstream/main
    git pull --rebase origin main
    git stash pop

    git submodule foreach 'git stash -u'
    git submodule foreach 'git pull origin main'
    git submodule foreach 'git stash pop'

# 1. Force every submodule to check out its configured branch

# 2. Pull down the latest updates for those branches
git submodule update --remote

    git submodule update --init --recursive --depth 1 --remote
    git push https://github.com/YourAccunt/MyEmEServer
    ```

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

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme_plugin_app.git plugins/app

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-community.git plugins/community

    git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-system.git plugins/system
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