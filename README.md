# eme-server

-Initializing Project

Add Modules:
 git submodule add -b main --depth 1  https://github.com/entermedia-community/eme-lib.git plugins/eme-lib
 git submodule add -b main --depth 1  https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder

---------------------------------------------------------------------------------
-Deploy an instance

Fork server and then add upstream and fetch:
cd eme-server-myserver
git remote add upstream https://github.com/entermedia-community/eme-server.git
git fetch upstream

Init submodules:
git submodule update --init --recursive --depth 1
---------------------------------------------------------------------------------

