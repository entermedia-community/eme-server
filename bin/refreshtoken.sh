#!/usr/bin/env bash
# refreshtoken.sh — print a Bearer token for the EME mediadb API, for AI agents and scripts.
#
# Logs in with an Ed25519 key pair (grant_type=key on token.json). The private key never leaves
# this machine; the server only holds the matching public key (registered with registerkey.json).
# The 7-day token it returns is cached with its mint date and reused until it is older than
# EME_TOKEN_MAX_AGE (default 6 days, so it is always renewed before the server expires it).
#
# Settings, read from <repo root>/.env:
#   EME_USER=admin                  user the key was registered for (--register asks if unset)
#   EME_KEY_ID=9f2c...              key_id returned by registerkey.json
#   EME_PRIVATE_KEY=MC4CAQAw...     base64url PKCS#8 private key (written by --keygen)
#   EME_TIMESTAMP / EME_NONCE / EME_SIGNATURE   last signature over "{user}\n{key_id}\n{timestamp ms}\n{nonce}",
#                                   saved with the key by --register and re-signed on any run once it is 24h old
#   EME_APP_PASSWORD=emeapp.<id>.<secret>   optional; if set it is used as-is instead of key login
#   EME_SERVER=http://localhost:8080/site   optional, this is the default
#   EME_TOKEN_MAX_AGE=518400        optional, seconds a cached token is reused
#
# Usage:
#   AUTH="Authorization: Bearer $(bin/refreshtoken.sh)"
#   curl -H "$AUTH" http://localhost:8080/site/mediadb/ai/mcp/
#
#   bin/refreshtoken.sh --force     ignore the cache and log in again (e.g. after a 401)
#   bin/refreshtoken.sh --register  one-time setup: creates a key pair if needed, emails a login code
#                                   to EME_USER (default admin), asks you for it, registers the public
#                                   key and stores EME_USER / EME_KEY_ID in .env. Also runs by itself
#                                   when a person (not an agent) runs the script without a key.
#   bin/refreshtoken.sh --app-password  same emailed-code check, but registers an application password
#                                   for tools that cannot sign (curl in other apps, CI). It is shown once and
#                                   saved as EME_APP_PASSWORD; the script then prints it as the Bearer value.
#   bin/refreshtoken.sh --signature print the signed login JSON as one line, for tools that sign in themselves:
#                                   {"username":..,"key_id":..,"timestamp":..,"nonce":..,"signature":..}
#                                   The stored timestamp/nonce/signature are re-signed first if they are 24h old or more.
#   bin/refreshtoken.sh --keygen    only create the key pair and print the public key
#
# Prints only the token on stdout. Errors go to stderr, exit code 1.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# read .env file location from parameters
while [ $# -gt 0 ]; do
  case "$1" in
    --env-file)
      ROOT="$2"
      shift 2
      ;;
    *)
      break
      ;;
  esac
done
  
ENV_FILE="$ROOT/.env"
CACHE_FILE="$ROOT/.refreshtoken"   # "<epoch seconds> <token>", mode 600, git-ignored

envval() {  # envval <KEY> — read KEY=value from .env, strip optional quotes
  [ -f "$ENV_FILE" ] || return 0
  sed -n "s/^$1=//p" "$ENV_FILE" | head -1 | sed 's/^["'\'']//; s/["'\'']$//'
}

die() { echo "refreshtoken: $*" >&2; exit 1; }

b64url_enc() { base64 -w0 | tr '+/' '-_' | tr -d '='; }
b64url_dec() {
  local s; s="$(tr '_-' '/+')"
  while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done
  printf '%s' "$s" | base64 -d
}

SERVER="$(envval EME_SERVER)"; SERVER="${SERVER:-http://localhost:8080/site}"

set_env() {  # set_env <KEY> <value> — replace the KEY= line in .env, or append it
  touch "$ENV_FILE"; chmod 600 "$ENV_FILE"
  if grep -q "^$1=" "$ENV_FILE"; then
    # portable in-place replace (GNU and BSD sed differ on -i); awk avoids escaping issues in the value
    awk -v k="$1" -v v="$2" 'index($0, k "=") == 1 { print k "=" v; next } { print }' "$ENV_FILE" > "$ENV_FILE.tmp" \
      && cat "$ENV_FILE.tmp" > "$ENV_FILE" && rm -f "$ENV_FILE.tmp"
  else
    printf '%s=%s\n' "$1" "$2" >> "$ENV_FILE"
  fi
}

# sign_login <user> <key_id> <private key> — sets TIMESTAMP (ms), NONCE and SIGNATURE for a key login
sign_login() {
  local work secs ns
  work="$(mktemp -d)"

  # Millisecond timestamp. Some date implementations (e.g. uutils coreutils) ignore the %3N width and
  # print all 9 nanosecond digits, so take the first 3 of %N ourselves; fall back to whole seconds if %N is missing.
  secs="$(date +%s)"
  ns="$(date +%N 2>/dev/null || true)"
  if printf '%s' "$ns" | grep -q '^[0-9]\{3,\}$'; then
    TIMESTAMP="$(( secs * 1000 + 10#$(printf '%s' "$ns" | cut -c1-3) ))"
  else
    TIMESTAMP="$(( secs * 1000 ))"
  fi
  NONCE="$(openssl rand -hex 16)"
  printf '%s\n%s\n%s\n%s' "$1" "$2" "$TIMESTAMP" "$NONCE" > "$work/message"

  # Use Python to sign with Ed25519 (OpenSSL 3.0 doesn't support Ed25519 signing via CLI)
  cat > "$work/sign.py" << 'EOF'
import sys
import base64
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ed25519

priv_b64url = sys.argv[1]
msg_path = sys.argv[2]

try:
    # Decode base64url
    priv_b64 = priv_b64url.replace('-', '+').replace('_', '/')
    while len(priv_b64) % 4:
        priv_b64 += '='
    priv_der = base64.b64decode(priv_b64)
    
    # Load as Ed25519 private key
    priv_key = serialization.load_der_private_key(priv_der, password=None)
    
    # Read and sign the message
    with open(msg_path, 'rb') as f:
        message = f.read()
    
    signature = priv_key.sign(message)
    
    # Output as base64url
    sig_b64 = base64.b64encode(signature).decode('ascii')
    sig_b64url = sig_b64.replace('+', '-').replace('/', '_').rstrip('=')
    print(sig_b64url, end='')
except Exception as e:
    print(f'Error: {e}', file=sys.stderr)
    sys.exit(1)
EOF

  SIGNATURE="$(python3 "$work/sign.py" "$3" "$work/message")" \
    || { rm -rf "$work"; die "could not sign; is EME_PRIVATE_KEY a valid Ed25519 key? (requires Python 3 with cryptography package)"; }

  rm -rf "$work"
}

# refresh_stored_signature [force] — keeps EME_TIMESTAMP / EME_NONCE / EME_SIGNATURE in .env next to the
# private key and key id. Re-signs with the existing key when the stored timestamp is 24 hours old or more.
refresh_stored_signature() {
  local user keyid priv ts
  user="$(envval EME_USER)"; keyid="$(envval EME_KEY_ID)"; priv="$(envval EME_PRIVATE_KEY)"
  [ -n "$user" ] && [ -n "$keyid" ] && [ -n "$priv" ] || return 0
  ts="$(envval EME_TIMESTAMP)"
  if [ "${1:-}" != "force" ] && [[ "$ts" =~ ^[0-9]+$ ]] && [ $(( $(date +%s) - ts / 1000 )) -lt 86400 ]; then
    return 0
  fi
  sign_login "$user" "$keyid" "$priv"
  set_env EME_TIMESTAMP "$TIMESTAMP"
  set_env EME_NONCE "$NONCE"
  set_env EME_SIGNATURE "$SIGNATURE"
}

public_from_private() { printf '%s' "$1" | b64url_dec | openssl pkey -inform DER -pubout -outform DER | b64url_enc; }

ensure_key() {  # creates EME_PRIVATE_KEY in .env if missing; prints the public key
  local priv; priv="$(envval EME_PRIVATE_KEY)"
  if [ -z "$priv" ]; then
    priv="$(openssl genpkey -algorithm ed25519 -outform DER | b64url_enc)"
    set_env EME_PRIVATE_KEY "$priv"
    echo "Created a new key pair; private key saved to $ENV_FILE (EME_PRIVATE_KEY)." >&2
  fi
  public_from_private "$priv"
}

if [ "${1:-}" = "--keygen" ]; then
  PUB="$(ensure_key)"
  echo "Public key (register it with registerkey.json): $PUB" >&2
  exit 0
fi

# Emails a login code to the user and reads it from the terminal (or EME_CODE). Sets $user and $code.
request_code() {
  local resp
  user="$(envval EME_USER)"; user="${user:-${EME_USER:-}}"
  if [ -z "$user" ]; then
    read -rp "Username to register for [admin]: " user </dev/tty; user="${user:-admin}"
  fi
  resp="$(curl -sS -X POST "$SERVER/mediadb/services/authentication/sendusercode.json" \
    --data-urlencode "username=$user")" || die "sendusercode.json call failed (is the server up at $SERVER?)"
  [ "$(printf '%s' "$resp" | jq -r '.response.status // empty')" = "ok" ] \
    || die "could not send a login code for '$user': $(printf '%s' "$resp" | jq -r '.response.status // "unexpected response"')"
  echo "A login code was emailed to $(printf '%s' "$resp" | jq -r '.response.email // "the address on file"')." >&2

  code="${EME_CODE:-}"
  if [ -z "$code" ]; then
    read -rp "Enter the code from the email: " code </dev/tty
  fi
}

# --register: prove who you are with an emailed code, then register this machine's public key
register() {
  local user code pub resp keyid
  pub="$(ensure_key)"
  request_code

  resp="$(curl -sS -X POST "$SERVER/mediadb/services/authentication/registerkey.json" \
    --data-urlencode "grant_type=otp" --data-urlencode "username=$user" --data-urlencode "code=$code" \
    --data-urlencode "publickey=$pub" --data-urlencode "label=$(hostname)")" \
    || die "registerkey.json call failed"
  keyid="$(printf '%s' "$resp" | jq -r '.key_id // empty')"
  [ -n "$keyid" ] || die "registration rejected: $(printf '%s' "$resp" | jq -r '.error // "unexpected response"')"

  set_env EME_USER "$user"
  set_env EME_KEY_ID "$keyid"
  refresh_stored_signature force
  rm -f "$CACHE_FILE"
  echo "Registered key $keyid for $user; saved EME_USER and EME_KEY_ID in $ENV_FILE." >&2
}

# --app-password: same check, but the server makes a secret for tools that cannot sign
register_app_password() {
  local user code resp secret
  request_code
  resp="$(curl -sS -X POST "$SERVER/mediadb/services/authentication/registerkey.json" \
    --data-urlencode "grant_type=otp" --data-urlencode "username=$user" --data-urlencode "code=$code" \
    --data-urlencode "keytype=apppassword" --data-urlencode "label=$(hostname) app password")" \
    || die "registerkey.json call failed"
  secret="$(printf '%s' "$resp" | jq -r '.apppassword // empty')"
  [ -n "$secret" ] || die "registration rejected: $(printf '%s' "$resp" | jq -r '.error // "unexpected response"')"
  set_env EME_USER "$user"
  set_env EME_APP_PASSWORD "$secret"
  echo "Application password saved as EME_APP_PASSWORD in $ENV_FILE (it cannot be shown again)." >&2
}

if [ "${1:-}" = "--register" ]; then
  register
  exit 0
fi

if [ "${1:-}" = "--app-password" ]; then
  register_app_password
  exit 0
fi

# Create/refresh the stored timestamp/nonce/signature when missing or 24 hours old or more (needs EME_USER,
# EME_KEY_ID and EME_PRIVATE_KEY); done before the app-password exit so it runs in every mode
refresh_stored_signature

# --signature: print the stored login signature as JSON (already refreshed above if it was 24h old)
if [ "${1:-}" = "--signature" ]; then
  USER_ID="$(envval EME_USER)"; KEY_ID="$(envval EME_KEY_ID)"
  [ -n "$USER_ID" ] && [ -n "$KEY_ID" ] && [ -n "$(envval EME_SIGNATURE)" ] \
    || die "no registered key in $ENV_FILE; run 'bin/refreshtoken.sh --register' once from a terminal"
  jq -cn --arg username "$USER_ID" --arg key_id "$KEY_ID" --arg timestamp "$(envval EME_TIMESTAMP)" \
    --arg nonce "$(envval EME_NONCE)" --arg signature "$(envval EME_SIGNATURE)" \
    '{username:$username,key_id:$key_id,timestamp:$timestamp,nonce:$nonce,signature:$signature}'
  exit 0
fi

# An application password needs no login or cache
APP_PASSWORD="$(envval EME_APP_PASSWORD)"
if [ -n "$APP_PASSWORD" ]; then
  printf '%s\n' "$APP_PASSWORD"
  exit 0
fi

FORCE=0; [ "${1:-}" = "--force" ] && FORCE=1
MAX_AGE="$(envval EME_TOKEN_MAX_AGE)"; MAX_AGE="${MAX_AGE:-518400}"

# 1. Cached token, if younger than MAX_AGE
if [ "$FORCE" -eq 0 ] && [ -f "$CACHE_FILE" ]; then
  read -r MINTED CACHED < "$CACHE_FILE" || true
  if [ -n "${CACHED:-}" ] && [[ "${MINTED:-}" =~ ^[0-9]+$ ]] && [ $(( $(date +%s) - MINTED )) -lt "$MAX_AGE" ]; then
    printf '%s\n' "$CACHED"
    exit 0
  fi
fi

# 2. Log in by signing "{user}\n{key_id}\n{timestamp ms}\n{nonce}" with the private key
USER_ID="$(envval EME_USER)"; KEY_ID="$(envval EME_KEY_ID)"; PRIV="$(envval EME_PRIVATE_KEY)"
if [ -z "$USER_ID" ] || [ -z "$KEY_ID" ] || [ -z "$PRIV" ]; then
  if [ -t 0 ] && [ -t 2 ]; then   # a person is at the terminal: run the one-time setup
    register
    USER_ID="$(envval EME_USER)"; KEY_ID="$(envval EME_KEY_ID)"; PRIV="$(envval EME_PRIVATE_KEY)"
  else
    die "no registered key in $ENV_FILE; run 'bin/refreshtoken.sh --register' once from a terminal"
  fi
fi

# Each login signs a fresh timestamp/nonce (the server rejects old ones); the stored copy is refreshed daily above
sign_login "$USER_ID" "$KEY_ID" "$PRIV"

RESPONSE="$(curl -sS -X POST "$SERVER/mediadb/services/authentication/token.json" \
  --data-urlencode "grant_type=key" --data-urlencode "username=$USER_ID" \
  --data-urlencode "key_id=$KEY_ID" --data-urlencode "timestamp=$TIMESTAMP" \
  --data-urlencode "nonce=$NONCE" --data-urlencode "signature=$SIGNATURE")" \
  || die "token.json call failed (is the server up at $SERVER?)"

TOKEN="$(printf '%s' "$RESPONSE" | jq -r '.access_token // empty' 2>/dev/null || true)"
if [ -z "$TOKEN" ]; then
  DETAIL="$(printf '%s' "$RESPONSE" | jq -r '.error_description // .error // empty' 2>/dev/null || true)"
  die "login rejected: ${DETAIL:-unexpected response}. Check EME_USER/EME_KEY_ID, that the key is not revoked, and that the clock is within 5 minutes of the server"
fi

# 3. Cache with the mint date (atomic replace, owner-only)
( umask 077; printf '%s %s\n' "$(date +%s)" "$TOKEN" > "$CACHE_FILE.tmp" )
mv "$CACHE_FILE.tmp" "$CACHE_FILE"

printf '%s\n' "$TOKEN"
