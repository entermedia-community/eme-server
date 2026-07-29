# eme-server

Instructions to run an eme-server

## AI Developer Setup
1. Setup Developer environment
    ```
    curl -fsSL get-eme.eme.world | bash -s -- help
    
    ##This will clone the eme repo. To save your changes you should fork the local repo and push your own git repository

    curl -L get-eme.eme.world | bash -s -- developer $HOME/git/MyEmEServer
    ```

## Developer Setup for custom client
1. Fork a copy of eme-server proyect in github.com

2. Clone the new project 
    ```
    git clone https://github.com/entermedia-community/eme-server-myserver.git
    ```
3. Pull plugins
    ```
    cd eme-server-myserver
    bin/pluginspull.sh
    ```

## Docker Deployment for custom client
1. Clone the new project 
    ```
    git clone https://github.com/entermedia-community/eme-server-myserver.git 
    ```
3. Pull plugins
    ```
    cd eme-server-myserver
    bin/pluginspull.sh
    ```
4. Create Docker instance: eme.sh dockercreate {server path} {node number} {user}
    ```
    bin/eme.sh dockercreate /full/path/eme-server-myserver 20 entermedia
    ```



