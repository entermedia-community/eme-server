#!/bin/bash
# Install scoped NOPASSWD sudo rules for the deploy-eme-server skill flow.
#
# Usage (run once per machine, in a real terminal):
#   sudo install-sudoers.sh [username] [deploy-root]
#
# username defaults to the invoking user (the one who ran sudo).
# On most machines that is "entermedia". The rule file is written to
# /etc/sudoers.d/eme-deploy-<username> so several users can each have their own.
#
# deploy-root (default /media/emsites) scopes the chown rule: eme.sh runs
# `sudo chown <user>:<group> <server-dir>` during init/dockerbuild, where
# <server-dir> is <deploy-root>/eme-server-<client>. Re-run with a new root
# if you change DEPLOY_ROOT; re-running always overwrites the rule file.

set -euo pipefail

if [[ $(id -u) -ne 0 ]]; then
    echo "ERROR: run with sudo, e.g.:  sudo $0 entermedia" >&2
    exit 1
fi

USERNAME="${1:-${SUDO_USER:-}}"
if [[ -z "$USERNAME" ]]; then
    echo "ERROR: could not determine the user. Pass it explicitly:  sudo $0 entermedia" >&2
    exit 1
fi

if ! id "$USERNAME" >/dev/null 2>&1; then
    echo "ERROR: user '$USERNAME' does not exist on this machine." >&2
    exit 1
fi

# If the user already has full passwordless sudo, nothing to do.
for f in /etc/sudoers.d/*; do
    if grep -qsE "^${USERNAME} .*NOPASSWD:ALL" "$f" 2>/dev/null; then
        echo "NOTE: $USERNAME already has a NOPASSWD:ALL rule ($f); skipping scoped install."
        exit 0
    fi
done

# Resolve real binary paths on THIS machine so the rule stays portable.
DOCKER_BIN="$(command -v docker || true)"
NGINX_BIN="$(command -v nginx || true)"
SYSTEMCTL_BIN="$(command -v systemctl || true)"
CHOWN_BIN="$(command -v chown || true)"
for pair in "docker:$DOCKER_BIN" "nginx:$NGINX_BIN" "systemctl:$SYSTEMCTL_BIN" "chown:$CHOWN_BIN"; do
    name="${pair%%:*}"; path="${pair#*:}"
    if [[ -z "$path" ]]; then
        echo "ERROR: $name not found on PATH; install it before deploying." >&2
        exit 1
    fi
done

DEPLOY_ROOT="${2:-/media/emsites}"
# Normalize a trailing slash so the sudoers glob matches <deploy-root>/eme-server-<client>.
DEPLOY_ROOT="${DEPLOY_ROOT%/}"

RULE="$USERNAME ALL=(root) NOPASSWD: $NGINX_BIN -t, $SYSTEMCTL_BIN reload nginx, $DOCKER_BIN *, $CHOWN_BIN * ${DEPLOY_ROOT}/*, /usr/bin/cp /tmp/opencode/nginx-deploy/*.conf /etc/nginx/sites-available/, /usr/bin/ln -sf /etc/nginx/sites-available/*.conf /etc/nginx/sites-enabled/"

TMP="$(mktemp)"
{
    echo "# Installed by deploy-eme-server skill (install-sudoers.sh) for $USERNAME"
    echo "$RULE"
} > "$TMP"

if ! visudo -cf "$TMP"; then
    echo "ERROR: generated rule failed visudo validation; nothing installed." >&2
    rm -f "$TMP"
    exit 1
fi

install -m 0440 -o root -g root "$TMP" "/etc/sudoers.d/eme-deploy-$USERNAME"
rm -f "$TMP"

echo "Installed /etc/sudoers.d/eme-deploy-$USERNAME:"
cat "/etc/sudoers.d/eme-deploy-$USERNAME"
echo ""
echo "Verify as that user (chown probe uses a throwaway dir under $DEPLOY_ROOT):"
echo "  sudo -u $USERNAME bash -c 'mkdir -p \"$DEPLOY_ROOT/.sudoprobe\" && sudo -n chown $USERNAME:$USERNAME \"$DEPLOY_ROOT/.sudoprobe\" && rmdir \"$DEPLOY_ROOT/.sudoprobe\" && sudo -n docker ps >/dev/null && sudo -n nginx -t && echo SUDO_RULES_OK'"
