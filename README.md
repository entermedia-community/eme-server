
# eme-server

Instructions to run a single eme-server, fork and init a new eme-server-client and instructions for setup Develoment environments to work with eme-server and eme-plugins.

## Single eme-server instance docker Install

1. `curl -o eme-docker-init.sh -jL https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/plugins/system/resources/docker/scripts/eme-docker-init.sh`
2. `sudo bash ./eme-docker-init.sh eme-server-myserver 100`


## Setup Client development environment

1. Fork server
    ##### Existing project:
    ```
    cd eme-server-myserver
    git init
    ```
    ##### Or clone new:
    ```
    git clone git clone https://github.com/entermedia-community/eme-server-myserver.git
    cd eme-server-myserver
    ```

2. Set local default branch
   ```
   git config --global init.defaultBranch main
   ```

3. Add *eme-server* upstream
    ```
    git remote add upstream https://github.com/entermedia-community/eme-server.git
    ```
4. Fetch & Merge
    ```
    git fetch upstream
    git merge upstream/main
    ```
5. Update Submodules
    ```
    git submodule update --init --recursive --depth 1
    ```
6. Create *toupstream* branch and keep always in-sync with eme-server. Work in the main version any customization you may have for your own server.

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