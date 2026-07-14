# eme-server-minsur

Docker Install Instructions
---
install eme-server instance:
curl -o eme-docker-init.sh -jL https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/plugins/system/resources/docker/scripts/eme-docker-init.sh
sudo bash ./eme-docker-init.sh eme-server-myserver 100

cd eme-server-myserver
git remote set-url origin https://github.com/entermedia-community/eme-server-minsur.git
git fetch
*Resolve conflicts, may need to add useremail/username 
git pull origin main
---

Development Instructions
---
Fork server and then add upstream and fetch
Existing:
-cd eme-server-myserver
-git init
Or Clone:
-git clone git clone https://github.com/entermedia-community/eme-server-myserver.git
-cd eme-server-myserver

git config --global init.defaultBranch main
git remote add upstream https://github.com/entermedia-community/eme-server.git
git fetch upstream
git merge upstream/main

Init submodules:

git submodule update --init --recursive --depth 1
---

Instructions for initializing Project Only (New Client)
---
Add SubModules:
git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder
---

Deleting Submodules
git submodule deinit -f plugins/eme-lib
git rm --cached -r plugins/eme-lib
*Manually delete Plugin entry in .gitmodules
