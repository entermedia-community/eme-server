---
name: update-app-css
description: Use when changing the look of the EnterMedia "find" app with CSS - colors, buttons, navbar, sidebar, tabs, tables, footer, logo size, light vs default theme. Explains which CSS file to edit (theme.css vs a theme's custom.css vs overridestemplate.css), the --themed-* variable system, the lighttheme class, and how to verify changes against the running server at http://localhost:8080/site/find/ using the Bearer auto-login header.
---

# Skill: update-app-css

CSS for the finder app lives under `plugins/finder/html/find/theme/`.

## Which file to edit

| File | Purpose | Edit when |
|---|---|---|
| `styles/theme.css` | Structural/base CSS for the whole app. Consumes `--themed-*` variables and has `.lighttheme ...` overrides. | Changing layout, spacing, component rules, or adding a new rule for both themes. |
| `defaulttheme/custom.css` | Dark/default theme: `:root` `--themed-*` values + small per-theme selector overrides. | Changing default (dark) theme colors. |
| `themelight/custom.css` | Light theme: same variable names, light values (also sets `--default-text`). | Changing light theme colors. |
| `styles/overridestemplate.css` | Velocity template (`#checkval($theme.get("sidebar"), "#151515")`) that generates `<appid>/theme/<themeid>/custom.css` from the `theme` data record via `ThemeModule.setTheme`. | Adding a new themeable variable that users set in the theme editor; keep the fallback equal to the default theme value. |
| `styles/colors.css`, `styles/custom.css`, `styles/layout.css`, `styles/mobile.css`, `styles/dropdown.css`, `styles/pages/*.css` | Legacy/page-specific CSS. `mobile.css` for small screens; `pages/*.css` for a single page (results, mediaplayer, categorypicker). | Only when the change is scoped to that page/breakpoint. |

Load order and `<style>` includes are declared in `plugins/finder/html/index.xconf`.

## Rules of thumb

1. **Colors go in variables, not in rules.** Add/adjust a `--themed-*` variable and consume it in `theme.css` with `var(--themed-...)`. Never hardcode a theme color in `theme.css` if it differs between themes.
2. **A new variable must be added to every theme**: `defaulttheme/custom.css`, `themelight/custom.css`, and `overridestemplate.css` (with a fallback). Missing in one = broken in that theme.
3. **Variable families** (all `--themed-` prefixed): `sidebar[-text]`, `navbar-primary[-text[-hover]]`, `navbar-secondary[-text]`, `nav-btn[-hover|-text]`, `nav-btn-active[-hover|-text]`, `tab-bar[-hover]`, `tab-btn[-text|-hover|-hover-text]`, `tab-btn-active[-text|-hover]`, `btn-cta[-plain|-hover|-text]`, `btn[-hover|-text]`, `btn-sec[...]`, `btn-acc[...]`, `btn-acc-active[...]`, `th[-hover|-text]`, `td[-stripe|-text]`, `footer[-text]`, `logo-width/height`. Buttons/nav-btn values are `linear-gradient(45deg, A 40%, B 100%)` (hover uses 50%); the CTA also has a `-plain` solid color.
4. **Light-theme-only tweaks** use the `html.lighttheme` / `.lighttheme <selector>` prefix in `theme.css` (the class is on `<html>`). Non-color base tokens (`--light-border`, `--default-text`, `--btn-radius`, `--raised-shadow`...) are in `:root` at the top of `theme.css`, with light overrides in `html.lighttheme {}`.
5. Match existing style: tab indentation, one selector per line, lowercase/uppercase hex as in the neighboring lines.
6. Per-theme selector overrides (e.g. `#header`, `.entityNavBarContainer`, `.emnav .navtabitem`) go in that theme's `custom.css` below `:root`, not in `theme.css`.
7. The `.btn` rule uses `border: none !important` and many `.btn-cta` rules use `!important`; override with a more specific selector or `!important` only when needed.

## Workflow

1. Identify the target via the table above; read the surrounding rules in `theme.css` (`grep -n "selector" plugins/finder/html/find/theme/styles/theme.css` - it is ~2000+ lines, do not read it whole).
2. Make the edit; apply to both themes if it is a variable.
3. Confirm the file is served (see below) - the server reads the plugin html directly, so a browser hard-reload (Ctrl+Shift+R) is usually enough; no restart for CSS-only changes. If the change is to `overridestemplate.css`, the generated `<appid>/theme/<themeid>/custom.css` only refreshes when the theme is saved/regenerated in the theme editor (`ThemeModule.setTheme`).
4. Verify visually.

## Server control (`eme.sh`)

`bin/eme.sh` now supports `start`, `stop` and `restart`: `eme.sh <start|stop|restart> [server-path]`.

- Plain HTML/CSS edits do **not** need a restart; a browser reload is enough.
- Editing an `.xconf` file, or adding/removing an HTML or `.xconf` file, needs the page cache cleared or a restart: `bin/eme.sh restart <server-path>`.
- Run `restart` yourself when needed instead of asking the user to do it. Do not run as root.
- `start` runs in the **foreground** (Ctrl-C/SIGTERM stops Tomcat), so from an agent/script run it detached and poll until it answers (about 30s; unauthenticated `/site/find/` returns 302 once up, `000` means not up yet):

```bash
(setsid nohup bin/eme.sh start /home/shanti/git/eme-server > "$SCRATCH/eme.log" 2>&1 < /dev/null &)
for i in $(seq 1 40); do
  c=$(curl -s -o /dev/null -w "%{http_code}" --max-time 3 http://localhost:8080/site/find/)
  [ "$c" != "000" ] && break; sleep 3
done; echo "code=$c"
```

- Stop it with `bin/eme.sh stop <server-path>`; `start` refuses to run if a PID file shows it is already running.

## Verifying against the running server

App URL: `http://localhost:8080/site/find/`

Auto-login by sending the HTTP header `Authorization: Bearer <admin token>` (handled by `BaseAutoLogin.java`). Token for this dev server (local dev only - do not commit it into other files):

```
Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc
```

**The header is only needed on the first call.** The server answers it with `Set-Cookie: JSESSIONID=...` (HttpOnly) and `Set-Cookie: emekey=<token>` (about 400 days), and later calls work with the cookies alone. Verified 2026-09-25:

| Call | Result |
|---|---|
| no header, no cookie | 302 to `/site/find/authentication/nopermissions.html` |
| Bearer header, `-c jar.txt` | 200, sets `JSESSIONID` + `emekey` |
| `-b jar.txt`, no header | 200, same page |

```bash
# first call: log in with the header and save cookies
curl -s -c jar.txt -H "Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc" \
  http://localhost:8080/site/find/ -o /dev/null -w "%{http_code}\n"

# every later call: cookie jar only, no header
curl -s -b jar.txt http://localhost:8080/site/find/theme/styles/theme.css | grep -n "your-selector"
```

Keep `jar.txt` in the scratchpad, not the repo. Use `-b jar.txt -c jar.txt` if you want it refreshed.

Quick checks with the header on every call (no cookie jar):

```bash
# page renders and is authenticated
curl -s -H "Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc" \
  http://localhost:8080/site/find/ | head -50

# the CSS you edited is being served with your change
curl -s -H "Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc" \
  http://localhost:8080/site/find/theme/styles/theme.css | grep -n "your-selector"
```

For a real visual check use a browser tool (or `cua-driver` per AGENTS.md - screenshots as the primary surface). A browser cannot set headers by URL alone; use a header-injecting browser tool/extension, or once logged in the session cookie persists. Toggle the light theme by switching theme in the app (adds `lighttheme` to `<html>`), and check **both** themes plus a narrow (<=768px) viewport when touching layout.

## Checklist before finishing

- [ ] Variable added/changed in `defaulttheme/custom.css` **and** `themelight/custom.css` **and** `overridestemplate.css`
- [ ] No hardcoded theme colors added to `theme.css`
- [ ] Verified served CSS contains the change and viewed both themes
- [ ] No unrelated reformatting of the large files
