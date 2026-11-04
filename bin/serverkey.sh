#!/usr/bin/env bash
# serverkey.sh — create this server's Ed25519 key pair for server-to-server requests.
#
# Every EME server (the root, e.g. eme.world, and each child, e.g. impactbank.world) has one key pair.
# The private key stays in this server's .env and signs requests it sends to other servers. The public
# key is given to the other side:
#   - on a child: paste it into the root's emeserver record for this server (field serverkey)
#   - on the root: give it to each child (its rootserverkey setting)
# Keys use the same encoding as StringEncryption: base64url without padding, PKCS#8 private key and
# X.509 (SubjectPublicKeyInfo) public key, so StringEncryption.sign / verifySignature accept them.
#
# Written to <repo root>/.env (mode 600, git-ignored):
#   EME_SERVER_PRIVATE_KEY=MC4CAQAw...   private key, never share it
#   EME_SERVER_KEY_ID=3f9a1c...          fingerprint of the public key, sent as X-EME-KeyId
#   EME_SERVER_PREVIOUS_PRIVATE_KEY / EME_SERVER_PREVIOUS_KEY_ID   the replaced pair, after --rotate
#
# Usage:
#   bin/serverkey.sh            create the key pair if missing, then print its key id and public key
#   bin/serverkey.sh --show     print the key id and public key of the existing pair (fails if none)
#   bin/serverkey.sh --rotate   make a new pair; the old one is kept as EME_SERVER_PREVIOUS_* so
#                               requests keep working until the other side has the new public key
#
# Prints the public key alone on the last stdout line (bin/serverkey.sh | tail -n1). Messages go to
# stderr; errors exit 1.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$ROOT/.env"

die() { echo "serverkey: $*" >&2; exit 1; }

command -v openssl >/dev/null || die "openssl is required"
openssl genpkey -algorithm ed25519 -outform DER >/dev/null 2>&1 \
  || die "this openssl ($(openssl version)) cannot make Ed25519 keys; install OpenSSL 1.1.1 or newer"

envval() {  # envval <KEY> — read KEY=value from .env, strip optional quotes
  [ -f "$ENV_FILE" ] || return 0
  sed -n "s/^$1=//p" "$ENV_FILE" | head -1 | sed 's/^["'\'']//; s/["'\'']$//'
}

set_env() {  # set_env <KEY> <value> — replace the KEY= line in .env, or append it (GNU and BSD safe)
  touch "$ENV_FILE"; chmod 600 "$ENV_FILE"
  local tmp; tmp="$(mktemp "$ENV_FILE.XXXXXX")"
  grep -v "^$1=" "$ENV_FILE" > "$tmp" || true
  printf '%s=%s\n' "$1" "$2" >> "$tmp"
  chmod 600 "$tmp"; mv "$tmp" "$ENV_FILE"
}

b64url_enc() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
b64url_dec() {
  local s; s="$(tr '_-' '/+')"
  while [ $(( ${#s} % 4 )) -ne 0 ]; do s="$s="; done
  printf '%s' "$s" | openssl base64 -d -A
}

new_private() { openssl genpkey -algorithm ed25519 -outform DER | b64url_enc; }
public_from_private() {
  printf '%s' "$1" | b64url_dec | openssl pkey -inform DER -pubout -outform DER 2>/dev/null | b64url_enc
}
key_id() {  # first 16 hex chars of SHA-256 over the public key DER
  printf '%s' "$1" | b64url_dec | openssl dgst -sha256 -r | cut -c1-16
}

save_new_pair() {
  local priv pub
  priv="$(new_private)"
  pub="$(public_from_private "$priv")"
  [ -n "$pub" ] || die "could not derive the public key"
  set_env EME_SERVER_PRIVATE_KEY "$priv"
  set_env EME_SERVER_KEY_ID "$(key_id "$pub")"
}

print_pair() {
  local priv pub
  priv="$(envval EME_SERVER_PRIVATE_KEY)"
  [ -n "$priv" ] || die "no EME_SERVER_PRIVATE_KEY in $ENV_FILE; run bin/serverkey.sh to create one"
  pub="$(public_from_private "$priv")"
  [ -n "$pub" ] || die "EME_SERVER_PRIVATE_KEY in $ENV_FILE is not a valid Ed25519 key"
  local kid; kid="$(key_id "$pub")"
  if [ "$(envval EME_SERVER_KEY_ID)" != "$kid" ]; then
    set_env EME_SERVER_KEY_ID "$kid"
  fi
  echo "Key id: $kid" >&2
  echo "Public key (give this to the other server; the private key stays in $ENV_FILE):" >&2
  echo "$pub"
}

case "${1:-}" in
  "")
    if [ -z "$(envval EME_SERVER_PRIVATE_KEY)" ]; then
      save_new_pair
      echo "Created a new server key pair in $ENV_FILE." >&2
    else
      echo "Server key pair already exists in $ENV_FILE (use --rotate to replace it)." >&2
    fi
    print_pair
    ;;
  --show)
    print_pair
    ;;
  --rotate)
    old="$(envval EME_SERVER_PRIVATE_KEY)"
    [ -n "$old" ] || die "no key pair to rotate; run bin/serverkey.sh to create one"
    set_env EME_SERVER_PREVIOUS_PRIVATE_KEY "$old"
    set_env EME_SERVER_PREVIOUS_KEY_ID "$(envval EME_SERVER_KEY_ID)"
    save_new_pair
    echo "Rotated. The old pair is kept as EME_SERVER_PREVIOUS_*; remove it once the other server has the new public key." >&2
    print_pair
    ;;
  -h|--help)
    sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
    ;;
  *)
    die "unknown option $1 (see --help)"
    ;;
esac
