#!/usr/bin/env bash
# refreshtoken.sh — print the current local admin Bearer token for the EME server.
#
# The token is stored in the root .env file (TOKEN=...). Resolution order:
#   1. TOKEN= in <repo root>/.env — printed as-is.
#   2. Otherwise, if .env has USER= and PASSWORD=, fetch a fresh token from the
#      server via the open getkey endpoint and persist it as TOKEN= in .env:
#        GET /site/mediadb/services/authentication/unused/getkey.json?id=<USER>&password=<PASSWORD>
#      (AdminModule.loadEnterMediaKey authenticates and returns
#       results.entermediakey = <user>md542<hash> — the Bearer token.)
#
# Usage:
#   AUTH="Authorization: Bearer $(bin/refreshtoken.sh)"
#   curl -H "$AUTH" http://localhost:8080/site/mediadb/ai/mcp/
#
# Prints only the token on stdout. Errors go to stderr, exit code 1.
# If a stored token stops working (302 / 403 after the seed changed), remove
# the TOKEN= line from .env and re-run — it will be re-fetched via USER/PASSWORD.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT/.env"

envval() {  # envval <KEY> — read KEY=value from .env, strip optional quotes
  [ -f "$ENV_FILE" ] || return 0
  sed -n "s/^$1=//p" "$ENV_FILE" | head -1 | sed 's/^["'\'']//; s/["'\'']$//'
}

# 1. Stored token
TOKEN="$(envval TOKEN)"
if [ -n "$TOKEN" ]; then
  printf '%s\n' "$TOKEN"
  exit 0
fi

# 2. Fetch a fresh one via getkey.json using USER=/PASSWORD= from .env
USER_ID="$(envval USER)"
PASSWORD="$(envval PASSWORD)"
if [ -z "$USER_ID" ] || [ -z "$PASSWORD" ]; then
  echo "refreshtoken: no TOKEN= and no USER=/PASSWORD= in $ENV_FILE" >&2
  exit 1
fi

RESPONSE="$(curl -sfG 'http://localhost:8080/site/mediadb/services/authentication/getkey.json' \
  --data-urlencode "id=$USER_ID" --data-urlencode "password=$PASSWORD")" || {
  echo "refreshtoken: getkey.json call failed (is the server up?)" >&2
  exit 1
}

# Success = results.entermediakey present; failure = results.status=invalidlogin
TOKEN="$(printf '%s' "$RESPONSE" | sed -n 's/.*"entermediakey"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
if [ -z "$TOKEN" ]; then
  echo "refreshtoken: invalidlogin — check USER=/PASSWORD= in $ENV_FILE" >&2
  exit 1
fi

# Persist for next time (replace existing TOKEN= line, or append)
touch "$ENV_FILE"
if grep -q '^TOKEN=' "$ENV_FILE"; then
  sed -i "s|^TOKEN=.*|TOKEN=$TOKEN|" "$ENV_FILE"
else
  printf 'TOKEN=%s\n' "$TOKEN" >> "$ENV_FILE"
fi

printf '%s\n' "$TOKEN"
