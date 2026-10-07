# AGENTS.md

## How `bin/eme.sh` works

`bin/eme.sh` is the main server-management entry point: `eme.sh <command> [server-path] [args]`.

- **Arguments**: `$1` = command (default `help`), `$2` = `SERVERHOME` (server directory), `$3` = node number (default 1) or commit message for `branchpush`.
- **Root check**: refuses to run as root, except `dockerstart`, which *requires* root.
- **Preflight** (for `developer|init|dockerbuild|update|branchpush`): creates `$SERVERHOME` if missing, then seeds a `.env` file with `INSTANCE`, `SITE`, and `NODENUMBER` if one doesn't exist.
- **Setup commands**:
  - `init` / `developer` / `dockerbuild`: chown the server dir to the invoking user (via `SUDO_USER`), clone the eme-server repo into it if no `.git` exists, run `bin/plugins.sh update`, and copy the default site from `webapp/system/templates/webapp/site` into `webapp/site` if missing.
  - `update`: git stash / pull main / stash pop in `$SERVERHOME`, then refresh plugins. after update make sure you run bin/restart.sh
  - `updatefork`: adds the `upstream` remote (entermedia-community/eme-server) and merges `upstream/main`.
  - `branchpush`: commits all changes (`$3` = message, default "Update from Client"), pulls, pushes to main.
  - `developer`: opens `eme-server.code-workspace` in VS Code.
- **`start`**: resolves `JAVA_HOME` (env, else sdkman or `/usr/lib/jvm/default-java`), runs `bin/compile.sh`, ensures `data` is a symlink to `webapp/WEB-INF/data`, expands `bin/resources/tomcat.args` into `tomcat/work/tomcat-args.txt` (substituting `$SERVERHOME`, chmod 600), then launches Tomcat via `java -Dappname=<name> @args-file org.apache.catalina.startup.Bootstrap start`.
- **Docker**:
  - `dockerbuild <path> <nodenumber> <ownedby>`: resolves the owner's UID/GID and runs `bin/resources/docker/scripts/eme-docker-init.sh`.
  - `dockerstart` (root-only, needs `USERID` env): creates the `entermedia` user/group with sudoers access if absent, starts Tomcat as that user via `eme start`, polls for the catalina PID, and traps SIGTERM to stop Tomcat cleanly before exiting 143.

## Fast visual `cua-driver` workflow

Use screenshots as the primary verification surface; the configured model accepts image input.

- Start with one compact state read:
  - `get_window_state(pid, window_id)` with a small `max_elements` value and a small `max_dimension` when a screenshot is enough.
- Use `query` to project AT-SPI output to the target label or role instead of reading the full tree.
- Prefer `element_token` from the latest snapshot for clicks, typing, scrolling, and verification.
- Refresh state after navigation, dialogs, tab changes, or any stale-token error.
- For Chrome/EME:
  - Use browser tools when available.
  - Use clipboard paste for text entry when direct typing is unreliable.
  - Use foreground delivery for input actions that require focus.
- Verify with `verify_state` using a small number of exact predicates; include a screenshot only when the predicate cannot prove the result.
- Avoid repeated full-tree dumps. Re-snapshot only after UI state changes.

## Local login credentials (VS Code / local development)

When working in VS Code against the local server, the login credentials are always the same. To authenticate HTTP requests (curl, MCP endpoints, JSON APIs), add this header:

```
Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc
```

Example:

```bash
curl -H 'Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc' \
  http://localhost:8080/site/mediadb/ai/mcp/
```

## After editing Java code

Any time you change `.java` files, `plugin.xml`, or jars under `plugins/*/lib`, run `bin/restart.sh` so the running server picks up the change. Do not test against the old server, and do not start Tomcat yourself with `eme.sh start`.

```bash
bin/restart.sh
```

- On localhost it restarts the VS Code debug session through the Remote Control extension (`bin/start.sh` installs it if missing). The Java extension recompiles before relaunching.
- If no debug session is running, it runs `bin/start.sh` to start one.
- It returns as soon as the restart is sent, so wait for the server before testing, e.g. poll `http://localhost:8080/site/` until it responds.
- It exits 1 if VS Code is not reachable on port 3710; report that rather than working around it.

## After editing `.xconf` files

Page configuration (`.xconf`) is cached by the page manager, so edits do not take effect until the cache is cleared. Any time an `.xconf` file is modified, clear the page manager cache with the Authorization header above (no restart needed):

```bash
curl -H 'Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc' \
  http://localhost:8080/openedit/views/filemanager/clearpagemanager.html
```

## After editing data list `.xml` files

Data lists (e.g. `plugins/catalog/html/data/lists/<searchtype>/*.xml`) are loaded into the database, so edits do not show up until the table's data is reset. For each table you changed, reset its data from XML and then clear the caches:

```bash
AUTH='Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc'
for t in automationscenario automationstep automationposition; do
  curl -H "$AUTH" "http://localhost:8080/site/find/views/settings/lists/datamanager/list/restoredata.html?searchtype=$t&oemaxlevel=1"
done
curl -H "$AUTH" http://localhost:8080/site/find/views/settings/status/tools/clearcaches.html
```

A reset overwrites records with what is in the XML files, so any edits made only in the UI are lost. It does **not** remove records that were deleted from (or renamed in) the XML; delete those explicitly, before resetting:

```bash
curl -H "$AUTH" -X DELETE "http://localhost:8080/site/mediadb/services/lists/data/<searchtype>/<id>"
```
