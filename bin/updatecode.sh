#!/bin/bash

#Initial clone
#git clone https://github.com/entermedia-community/eme-server-minsur.git

#First time setup 
#git remote add upstream https://github.com/entermedia-community/eme-server.git

#Update code from upstream
git fetch upstream
git merge upstream/main

#check for conflicts

git config pull.rebase false 

#Update submodules
git submodule update --init --recursive --depth 1