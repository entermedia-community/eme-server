# eme-server

<h2>Single eme-server instance docker Install Instructions</h2>

<h3>Install eme-server instance</h3>

1. <pre>curl -o eme-docker-init.sh -jL https://raw.githubusercontent.com/entermedia-community/eme-server/refs/heads/main/plugins/system/resources/docker/scripts/eme-docker-init.sh</pre>
2. <pre>sudo bash ./eme-docker-init.sh eme-server-myserver 100</pre>




<h3>Launch eme-server-CLIENT</h3>

1. Install eme-server instance (Previous Instructions)

2. <pre>cd eme-server-myserver
    git remote set-url origin https://github.com/entermedia-community/eme-server-minsur.git
    git fetch
    *Resolve conflicts, may need to add useremail/username 
    git pull origin main</pre>

&nbsp;
---
&nbsp;

<h2>Development Instructions</h2>

1. Fork server
    Existing:
        <pre>cd eme-server-myserver
        git init</pre>
    Or clone:
        <pre>git clone git clone https://github.com/entermedia-community/eme-server-myserver.git
        cd eme-server-myserver</pre>

2. Set local default branch
    <pre>git config --global init.defaultBranch main

3. Add Upstream
    <pre>git remote add upstream https://github.com/entermedia-community/eme-server.git</pre>
4. Fetch & Merge
    <pre>git fetch upstream
    git merge upstream/main</pre>
5. Update Submodules
    <pre>git submodule update --init --recursive --depth 1</pre>



<h3>Instructions for initializing Project Only (New Client)</h3>

<Strong>Add SubModules</strong>

<pre>git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-finder.git plugins/finder

git submodule add -b main --depth 1 https://github.com/entermedia-community/eme_plugin_app.git plugins/app

git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-community.git plugins/community

git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-system.git plugins/system
</pre>

<Strong>Deleting Submodules</Strong>

1. <pre>git submodule deinit -f plugins/eme-lib</pre>
2. <pre>git rm --cached -r plugins/eme-lib</pre>
3. Manually delete Plugin entry in .gitmodules


<h3>Add Plugins</h3>

<pre>git submodule add -b main --depth 1 https://github.com/entermedia-community/eme-plugin-NEWPLUGIN.git plugins/NEWPLUGIN</pre>
