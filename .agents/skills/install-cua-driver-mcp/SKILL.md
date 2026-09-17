---
name: install-cua-driver-mcp
description: Use when the user asks to install, configure, repair, or verify the cua-driver MCP service on a Linux X11 desktop in the same style as this machine. Covers the official installer, PATH setup, X11/AT-SPI prerequisites, daemon startup, OpenCode MCP configuration, and verification commands.
---

# Install cua-driver MCP on Linux (X11)

Use this skill to reproduce the working Linux Mint / Ubuntu-family X11 setup where `opencode` launches `cua-driver mcp`, which talks to a persistent `cua-driver serve` daemon.

## Target layout

After installation, expect these paths:

- Binary on PATH: `/home/USER/.local/bin/cua-driver`
- Managed package directory: `/home/USER/.cua-driver/packages/current/cua-driver`
- Release directory: `/home/USER/.cua-driver/packages/releases/<version>-x86_64-unknown-linux-gnu/`
- Daemon socket: `/home/USER/.cache/cua-driver/cua-driver.sock`
- Install channel marker: `/home/USER/.cua-driver/.telemetry_install_channel` containing `install_script`

On the reference machine, the observed running state was:

```text
/home/shanti/.local/bin/cua-driver serve --grant existing-profile
socket: /home/shanti/.cache/cua-driver/cua-driver.sock
permission mode: standard (built_in_default)
```

## Prerequisites

- Linux X11 desktop session, not headless.
- `DISPLAY` set, for example `:0.0`.
- User has a running D-Bus session bus.
- `curl`, `bash`, and `ca-certificates` are available.
- Accessibility stack is installed. On Debian/Ubuntu/Mint:

```bash
sudo apt update
sudo apt install -y at-spi2-core dbus-x11 x11-utils curl ca-certificates
```

Ensure accessibility environment variables are present in the user desktop session that runs `opencode` or the daemon:

```bash
export GTK_MODULES=gail:atk-bridge
export QT_ACCESSIBILITY=1
unset NO_AT_BRIDGE
```

If they are already present in the desktop session, do not force them into shell profiles.

## Install the binary

Use the official installer observed from this machine's `cua-driver` binary:

```bash
curl -fsSL https://cua.ai/driver/install.sh | bash
```

Then make sure `~/.local/bin` is on PATH:

```bash
echo 'export PATH="$HOME/.local/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

Verify:

```bash
command -v cua-driver
cua-driver --version
```

Expected result shape:

```text
cua-driver 0.28.2
```

## Verify Linux/X11/AT-SPI prerequisites

Run:

```bash
cua-driver doctor --json
```

Expected important probes:

- `binary`: ok
- `install dir`: points under `/home/USER/.cua-driver/packages/releases/...`
- `display server`: `X11 (DISPLAY=...)`
- `X11 connection`: connected and reports visible top-level windows
- `AT-SPI`: `org.a11y.Bus reachable via session bus`

If `AT-SPI` fails:

1. Confirm the command is running in the user desktop session, not a detached system service without D-Bus.
2. Install `at-spi2-core`.
3. Ensure `GTK_MODULES=gail:atk-bridge` and `QT_ACCESSIBILITY=1` are set for that session.
4. Re-run `cua-driver doctor --json`.

## Start the daemon

The reference machine runs:

```bash
cua-driver serve --grant existing-profile
```

Start it in the user desktop session if it is not already running:

```bash
nohup "$HOME/.local/bin/cua-driver" serve --grant existing-profile \
  > /tmp/cua-driver-serve.log 2>&1 &
```

Then verify:

```bash
cua-driver status
```

Expected result shape:

```text
Cua Driver daemon is running
  socket: /home/USER/.cache/cua-driver/cua-driver.sock
  pid: <pid>
  permission mode: standard (built_in_default)
```

Notes:

- Do not run the daemon as root.
- The MCP client may auto-start a daemon, but for a stable desktop setup it is better to start `serve` explicitly in the user session.
- If the machine needs existing Chromium profile attachment, keep `--grant existing-profile`.
- If the daemon is already running and healthy, do not start a second one.

## Configure OpenCode MCP

Add or update the MCP entry in the active OpenCode config file, usually `opencode.json` or `opencode.jsonc`:

```json
{
  "mcp": {
    "cua-driver": {
      "type": "local",
      "command": ["/home/USER/.local/bin/cua-driver", "mcp"],
      "enabled": true,
      "timeout": 30000
    }
  }
}
```

Replace `/home/USER` with the actual home directory.

After saving config or skill changes, quit and restart OpenCode. MCP/skill changes are not hot-reloaded.

On the reference machine, the project config contained:

```json
{
  "type": "local",
  "command": ["/home/shanti/.local/bin/cua-driver", "mcp"],
  "enabled": false,
  "timeout": 30000
}
```

For a fresh install, set `"enabled": true`.

## Verify from OpenCode

After restarting OpenCode:

1. Confirm `cua-driver_*` MCP tools are available.
2. Run a health or permissions check through the MCP surface if available.
3. Call `list_windows` with `on_screen_only: true` and confirm real desktop windows are returned.
4. If using GUI automation, test one small read-only window state capture on an existing app.

## Update later

Use the built-in update path:

```bash
cua-driver check-update --json
cua-driver update --apply
```

Then restart the daemon if it is running:

```bash
cua-driver stop
nohup "$HOME/.local/bin/cua-driver" serve --grant existing-profile \
  > /tmp/cua-driver-serve.log 2>&1 &
cua-driver status
```

## Troubleshooting

- `cua-driver: command not found`: add `~/.local/bin` to PATH and restart the shell or OpenCode.
- `doctor` says AT-SPI unavailable: run from the desktop user session with D-Bus and accessibility environment variables set.
- `doctor` says no X11 connection: confirm `DISPLAY` is set and the user can connect to the X server.
- Daemon not running after install: start `cua-driver serve --grant existing-profile` manually in the user session.
- OpenCode does not see tools: ensure `"enabled": true`, use the absolute binary path, and restart OpenCode.
- Wayland session: this skill documents the reference X11 setup. For Wayland, check whether XWayland is available and re-run `cua-driver doctor --json` before assuming the same commands will work.
