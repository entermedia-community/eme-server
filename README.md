# eme-server

Clone server
git clone -b main --depth 1 https://github.com/entermedia-community/eme-server.git  eme-server-myserver

Fork server and then add upstream and fetch:
cd eme-server-myserver
git remote add upstream https://github.com/entermedia-community/eme-server.git
git fetch upstream

Init submodules:
git submodule update --init --recursive --depth 1



Submodules

-eme-lib 
--git submodule add -b main  https://github.com/entermedia-community/eme-lib.git eme-lib

-eme-plugin 
--git submodule add -b main  https://github.com/entermedia-community/eme_plugin_app.git