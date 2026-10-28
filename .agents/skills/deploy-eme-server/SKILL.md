---
name: deploy-eme-server
description: Use when the user asks to deploy, provision, or stand up a new eme-server instance for a client on this machine — "deploy eme-server for X", "create a new client instance", "set up <client>.eme.world". Covers cloning the repo under /media/emsites, allocating a unique node number from the instance DB, dockerbuild, update, creating the nginx site from the template (domain base selectable, default eme.world), reloading nginx, and verifying in the browser.
---

# Deploy an eme-server instance

Provisions one client instance end to end: git clone → docker container on the `entermedia` network → nginx vhost → verified in the browser.

## What a finished deployment looks like

- Repo cloned at `$DEPLOY_ROOT/eme-server-<client>` (owned by the deploying user)
- Docker container `eme-server-<client><NNN>` running Tomcat at `http://172.18.0.<NNN>:8080` on the `entermedia` docker network (subnet `172.18.0.0/16`)
- Nginx vhost for `https://<client>.$DOMAIN_BASE` (default `eme.world`, wildcard cert) proxying to the container IP
- A row in the instance DB `$DEPLOY_ROOT/deployed-instances.txt`

## One-time prerequisite per machine: NOPASSWD sudo rules

The deploy flow needs passwordless root for a few commands (dockerbuild calls `sudo docker` **and** `sudo chown <user>:<group> <server-dir>` internally, nginx reload and the vhost install are root-only). The deploying user — usually **`entermedia`** — must be able to run them without a password.

Check first (run as the deploying user):

```bash
mkdir -p $DEPLOY_ROOT/.sudoprobe && sudo -n chown "$(id -un):$(id -gn)" "$DEPLOY_ROOT/.sudoprobe" \
  && rmdir "$DEPLOY_ROOT/.sudoprobe" && sudo -n docker ps >/dev/null && sudo -n nginx -t && echo SUDO_RULES_OK
```

If that fails, **ask the user to run this once in a real terminal** (the agent cannot type a sudo password). The installer takes the username (default: whoever ran sudo) and the deploy root (default `/media/emsites`, used to scope the chown rule), resolves the real binary paths on that machine, and validates with `visudo` before installing `/etc/sudoers.d/eme-deploy-<user>`:

```bash
sudo <repo>/.agents/skills/deploy-eme-server/install-sudoers.sh entermedia /media/emsites
```

Notes for other machines:

- If the user already has a `NOPASSWD:ALL` rule (the installer detects and skips this), no install is needed.
- The deploying user should be in the `docker` group (`sudo usermod -aG docker entermedia`) so plain `docker` works too.
- On stock systems `entermedia` may have `/bin/sh` as shell; `sudo bash -c '...'` works regardless, but `sudo usermod -s /bin/bash entermedia` makes interactive use nicer.

## Inputs

- **DEPLOY_ROOT** — where instances live; default `/media/emsites` (create it if missing, owned by the deploying user). The instance DB lives at `$DEPLOY_ROOT/deployed-instances.txt`.
- **CLIENTNAME** — from the user; lowercase, no spaces (e.g. `monday`). Everything derives from it:
  - `DIR=$DEPLOY_ROOT/eme-server-$CLIENTNAME`
  - `DOMAIN=$CLIENTNAME.$DOMAIN_BASE`
  - container name = `eme-server-$CLIENTNAME$NNN` (basename of DIR + node number)
- **DOMAIN_BASE** — which domain family the instance gets. Ask the user if not stated; default is the first option. Add new rows here as options appear:

  | Base | Cert dir | Notes |
  |---|---|---|
  | `eme.world` | `/etc/letsencrypt/live/eme.world` | default (cert must be issued before first deploy) |
  | `entermediadb.net` | `/etc/letsencrypt/live/entermediadb.net` | wildcard cert already on this machine |

- **Node number NNN** — between **2 and 250** (the script validates 1–250, but **node 1 is unusable**: the `entermedia` docker network's bridge gateway occupies `172.18.0.1`, so a container with that IP fails with "Address already in use"). Never reuse one that is already in the DB.

## Workflow

### 1. Check for an existing deployment

```bash
grep -i "$CLIENTNAME" $DEPLOY_ROOT/deployed-instances.txt   # path column = actual server dir (may be outside /media/emsites)
ls -d $DEPLOY_ROOT/eme-server-$CLIENTNAME 2>/dev/null
docker ps -a --format '{{.Names}}' | grep "^eme-server-${CLIENTNAME}"
```

If the dir, a DB row, or a container already exists: **stop and ask the user** whether to re-deploy (rebuild the container in place — `dockerbuild` stops/removes an existing container with the same name) or abort. Do not delete data without explicit confirmation.

### 2. Clone the repo

```bash
mkdir -p $DEPLOY_ROOT
cd $DEPLOY_ROOT
git clone https://github.com/entermedia-community/eme-server eme-server-$CLIENTNAME
chown -R "$(id -un)" $DEPLOY_ROOT/eme-server-$CLIENTNAME
```

(If the dir already exists from a previous attempt, `cd` into it and `git pull` instead.)

### 3. Allocate a node number (instance DB)

DB file: `$DEPLOY_ROOT/deployed-instances.txt`, one row per instance, comma-separated:

```
name,path,nodenumber,domain
```

Pick the smallest free number in 1–250 that is not in the `nodenumber` column:

```bash
awk -F, 'NR>1 {print $3}' $DEPLOY_ROOT/deployed-instances.txt | sort -n
```

Also sanity-check the docker side before committing the number (a container may exist without a DB row):

```bash
docker ps -aq --filter "name=eme-server-${CLIENTNAME}${NNN}"
```

### 4. Dockerbuild

```bash
cd $DEPLOY_ROOT/eme-server-$CLIENTNAME
bin/eme.sh dockerbuild /absolute/path/to/$DEPLOY_ROOT/eme-server-$CLIENTNAME $NNN entermedia
```

**Pass the absolute path**, never `.` — `eme.sh` derives `SERVERNAME` from `basename "$2"`, so `.` seeds `.env` with `INSTANCE=.$NNN`, `SITE=.` (delete the bad `.env` and re-run if this happened).

This pulls `entermediadb/eme-server:latest`, creates the `entermedia` network if missing, and starts the container with IP `172.18.0.$NNN`. It also seeds `.env` (`INSTANCE`, `SITE`, `NODENUMBER`, `PRIVATEKEY`) — write the DB row **after** this succeeds, using the values from `.env`.

**Ownership alignment (required when the deploying user ≠ `ownedby`)**: the preflight chowns the server dir to `${SUDO_USER:-$USER}` (the deployer), but the container runs as `ownedby` (`entermedia`). If they differ, the first boot crash-loops in the compile step with `mkdir: Permission denied` / `could not create parent directories`. Align ownership and restart:

```bash
sudo chown -R "$(id -u entermedia):$(id -g entermedia)" $DEPLOY_ROOT/eme-server-$CLIENTNAME
sudo docker restart eme-server-$CLIENTNAME$NNN
```

**Git "dubious ownership" (same trigger)**: once the tree is owned by `entermedia`, any `git`/`eme.sh update` run as the deployer fails with `detected dubious ownership`. Add safe.directory entries — the instance dir **and every plugin repo** (they are separate git repos). Git < 2.35.2 does not support wildcards in safe.directory, so add each path explicitly (no trailing slash):

```bash
for d in "$DEPLOY_ROOT/eme-server-$CLIENTNAME" "$DEPLOY_ROOT"/eme-server-$CLIENTNAME/plugins/*/; do
  git config --global --add safe.directory "${d%/}"
done
```

### 5. Update to latest

```bash
bin/eme.sh update .
```

Pulls latest main + updates plugins (needs the safe.directory entries from step 4). If the update changed Java code or plugins, re-align ownership (git writes files as the deployer) and restart the container so it picks them up:

```bash
sudo chown -R "$(id -u entermedia):$(id -g entermedia)" $DEPLOY_ROOT/eme-server-$CLIENTNAME
sudo docker restart eme-server-$CLIENTNAME$NNN
```

**Missing `_site.xconf` (fresh clones)**: `webapp/site` already exists in a fresh clone (tracked Flutter app assets), so eme.sh's "copy default site if missing" step is skipped and `webapp/site/_site.xconf` — which PageManager needs for the site's settings — never lands. Copy it from the template (chown the dir to yourself first, then back, since only the scoped `chown`/docker sudo rules exist):

```bash
sudo chown "$(id -un):$(id -gn)" $DEPLOY_ROOT/eme-server-$CLIENTNAME/webapp/site
cp $DEPLOY_ROOT/eme-server-$CLIENTNAME/webapp/system/templates/webapp/site/_site.xconf \
   $DEPLOY_ROOT/eme-server-$CLIENTNAME/webapp/site/_site.xconf
sudo chown "$(id -u entermedia):$(id -g entermedia)" $DEPLOY_ROOT/eme-server-$CLIENTNAME/webapp/site
```

(Use absolute paths in the `sudo chown` calls — the sudoers rule matches on the literal argument string.)

### 6. Register in the instance DB

Append exactly one row (keep the header line):

```bash
echo "$CLIENTNAME,$DEPLOY_ROOT/eme-server-$CLIENTNAME,$NNN,$DOMAIN" >> $DEPLOY_ROOT/deployed-instances.txt
```

### 7. Create the nginx vhost

Template: `.agents/skills/deploy-eme-server/nginx-site.conf.template` (relative to this repo). Replace placeholders:

| Placeholder | Value |
|---|---|
| `__DOMAIN__` | `$CLIENTNAME.$DOMAIN_BASE` |
| `__CERT_DIR__` | `/etc/letsencrypt/live/$DOMAIN_BASE` — must exist (wildcard cert for the base), check first |
| `__UPSTREAM_NAME__` | `eme_` + client name, non-alphanumerics → `_` (e.g. `eme_monday`) — must be unique across `/etc/nginx/sites-enabled/` |
| `__CONTAINER_IP__` | `172.18.0.$NNN` |
| `__PORT__` | `8080` |

```bash
# Wildcard cert for the chosen base must exist, or nginx -t will fail later
test -f "/etc/letsencrypt/live/$DOMAIN_BASE/fullchain.pem" \
  || { echo "No cert for $DOMAIN_BASE in /etc/letsencrypt/live/ — issue one first (needs DNS control of the base domain)."; exit 1; }

mkdir -p -m 700 /tmp/opencode/nginx-deploy
sed -e "s/__DOMAIN__/$DOMAIN/g" \
    -e "s|__CERT_DIR__|/etc/letsencrypt/live/$DOMAIN_BASE|g" \
    -e "s/__UPSTREAM_NAME__/eme_${CLIENTNAME}/g" \
    -e "s/__CONTAINER_IP__/172.18.0.$NNN/g" \
    -e "s/__PORT__/8080/g" \
  .agents/skills/deploy-eme-server/nginx-site.conf.template \
  > /tmp/opencode/nginx-deploy/$DOMAIN.conf
sudo cp /tmp/opencode/nginx-deploy/$DOMAIN.conf /etc/nginx/sites-available/
sudo ln -sf /etc/nginx/sites-available/$DOMAIN.conf /etc/nginx/sites-enabled/
```

(The agent has no write access to `/etc/nginx`, so the rendered file is staged in the 700-mode dir and moved by the scoped sudo rules.)

### 8. Reload nginx

```bash
sudo nginx -t && sudo systemctl reload nginx
```

If `nginx -t` fails, fix the config (usually a duplicate upstream name) and re-run; do not leave a broken config loaded.

### 9. Verify

The site's entry point is `/` → 302 → `/site/find` (per `webapp/index.xconf`). **Do not probe `/site/`** — it has no index page and returns 404 even on a healthy instance.

1. Container is up: `docker ps --filter "name=eme-server-$CLIENTNAME$NNN"` shows `Up`.
2. Tomcat answers inside the network (poll up to ~3 min after a restart; first boot compiles Java and is slow):
   `curl -sL -o /dev/null -w '%{http_code} %{url_effective}\n' http://172.18.0.$NNN:8080/` → final **200** at `/site/find/authentication/nopermissions.html` (anonymous).
3. Through nginx (if the domain doesn't resolve yet, force local resolution with `--resolve $DOMAIN:443:127.0.0.1`):
   `curl -skL -o /dev/null -w '%{http_code}\n' https://$DOMAIN/` → **200**.
4. Browser check with cua-driver: open Chrome to `https://$DOMAIN/`, take one compact screenshot (`get_window_state`, small `max_dimension`), confirm the EME find app renders (not a cert error or 502).

## Troubleshooting

- **dockerbuild dies with `sudo: a password is required`** (right after "Creating ... .env file") — the scoped `chown` rule is missing or was installed for a different deploy root. Re-run the installer with this machine's deploy root: `sudo <repo>/.agents/skills/deploy-eme-server/install-sudoers.sh <user> $DEPLOY_ROOT`, then re-run step 4 (it is idempotent; the clone and `.env` survive).
- **Container crash-loops with `mkdir: Permission denied` / `could not create parent directories`** in `docker logs` — deployer ≠ `ownedby`, so the tree is owned by the wrong user. Run the ownership alignment from step 4, then restart.
- **`git: detected dubious ownership`** when running `eme.sh update` or any git command as the deployer — the tree is owned by `entermedia`. Add the safe.directory entries from step 4 (one per repo; wildcards only work on git ≥ 2.35.2).
- **Domain doesn't resolve publicly after everything else works** — DNS is per-client: add an A record `$CLIENTNAME.$DOMAIN_BASE` → this machine's public IPv4 (`curl -4 ifconfig.me`) at the domain provider. The apex of the base domain may point to a *different* server; that does not affect subdomains. Verify locally first with `curl -sk --resolve $DOMAIN:443:127.0.0.1 https://$DOMAIN/`.
- **502 from nginx** — container down or still booting: `sudo docker logs --tail 100 eme-server-$CLIENTNAME$NNN`.
- **Cert error in browser** — domain must be a subdomain of the chosen `DOMAIN_BASE` and the wildcard cert must exist under `/etc/letsencrypt/live/<base>/`; check the rendered vhost's `server_name` and `ssl_certificate` paths.
- **`nginx -t` duplicate upstream** — another vhost already defines that upstream name; pick a different `__UPSTREAM_NAME__`.
- **dockerbuild fails on node number** — must be 1–250; re-run step 3.
- **Stale container from a manual run** — `sudo docker stop -t 60 <name> && sudo docker rm -f <name>` (only with user confirmation), then re-run dockerbuild.

## Rollback / teardown (only on explicit request)

```bash
sudo docker stop -t 60 eme-server-$CLIENTNAME$NNN && sudo docker rm -f eme-server-$CLIENTNAME$NNN
sudo rm /etc/nginx/sites-enabled/$DOMAIN.conf /etc/nginx/sites-available/$DOMAIN.conf
sudo nginx -t && sudo systemctl reload nginx
# remove the DB row, then ask before deleting $DEPLOY_ROOT/eme-server-$CLIENTNAME (client data!)
```
