---
name: create-html-page
description: Use when creating or modifying any HTML page in the EME server project — "make me a page", "add a view for X", "create an html file", building UI screens for the find app, adding .xconf page config, using Velocity in templates, layouts, or overriding a plugin page at site level. Covers where files go (site vs plugin fallback tree), the .html + .xconf page pair anatomy, Velocity conventions, translation, AJAX patterns, and how to make changes take effect on the running server.
---

# Skill: create-html-page

EME pages are **not standalone documents**. A page is a Velocity fragment (`.html`) plus optional page configuration (`.xconf`), resolved through a fallback tree and wrapped by layouts. Write it like one, or the page will render empty, unstyled, or twice-wrapped.

## Core mental model

1. **A page = `foo.html` + optional `foo.xconf`** in the same folder. The `.html` is a fragment (no `<html>`/`<body>` wrapper for app pages); a layout supplies the surrounding chrome.
2. **Fallback tree.** When a file is requested, EME looks in the site first, then falls back through plugins. The main app fallback is `/eme -> /finder/find -> /community/default`. A folder `webapp/site/<app>/...` shadows the same path in a plugin — that is how you customize without forking.
3. **`_site.xconf`** in a folder sets defaults (properties, path-actions, styles, permissions, inner-layout) for every page in that folder and its subfolders. A page's own `.xconf` merges on top of it.
4. **Layouts** are HTML files layered like an onion around the requested page; declared with `<inner-layout>` in a `.xconf`. The find app's base layout is `/${applicationid}/theme/layouts/applicationcontent.html`.

## Where to put a new page

| Change | Location |
|---|---|
| Customize/override an existing app page for this site | Mirror the path under `webapp/site/find/...` (e.g. override of `views/foo/bar.html` goes to `webapp/site/find/views/foo/bar.html`). Site files win over plugin files. |
| New feature/screen in the find app | `plugins/finder/html/find/views/<area>/...` |
| Community/project-oriented screens | `plugins/community/html/default/...` (or `html/blog`) |
| Shared JS/CSS libraries | `plugins/components/...` (served via `/${applicationid}/components/...`) |
| App-wide styles | `plugins/finder/html/find/theme/styles/` (see the `update-app-css` skill for theme rules) |

**Gotcha:** plugin directories (`plugins/finder`, `plugins/community`, ...) are separate git repos, ignored by the top-level repo. Commit inside the plugin folder, and note that grep/git tools run from the repo root may skip them — search inside the plugin directory directly.

## Page anatomy (real example)

`plugins/community/html/default/project/channel/edit.html`:

```html
#set( $searcher = $mediaarchive.getSearcher("userpost") )
$context.putPageValue("viewid", "userpostgeneral")

<div id="channeledit">
  <form name="savecommunityblog" id="savecommunityblog" class="userpostform"
        enctype="multipart/form-data"
        action="$apphome/project/channel/save.html" method="post">
    <input type="hidden" name="id" value="$!{data.id}" />
    <input class="btn btn-sm btn-primary me-2" type="submit" value="[[Save]]" />
    <a class="btn btn-sm btn-danger confirm ajax"
       data-confirm="[[Are you sure]]?" data-targetdiv="applicationcontent"
       data-closedialog="true" data-updateurl="true"
       data-url="${projectlink}/blog" data-oemaxlevel="1"
       href="${projectlink}/channel/delete.html?id=${data.id}">[[Delete]]</a>
  </form>
</div>
```

`edit.xconf` next to it:

```xml
<page>
	<path-action name="ProjectModule.loadCollection" />
	<path-action name="DataEditModule.loadData" allowduplicates="true">
		<pageval>data</pageval>
		<searchtype>userpost</searchtype>
	</path-action>
	<inner-layout>/${applicationid}/theme/layouts/dialoglayout.html</inner-layout>
</page>
```

### Velocity conventions (the `.html`)

- `#set( $x = ... )` to compute; `$var` to output.
- **Quiet references**: use `$!{var}` for anything that may be null — a bare `$var` prints the literal text if unset.
- `$context.putPageValue("name", value)` exposes a value to other includes/templates on the page.
- `$pages.include("/$applicationid/components/xml/detaileditorcss.html", $context)` pulls in shared fragments.
- **User-visible strings go in `[[...]]`** (translation filter) — e.g. `value="[[Save]]"`, `data-confirm="[[Are you sure]]?"`. Never hardcode display text outside brackets.
- **URLs from page values, not literals**: `${apphome}`, `${applicationid}`, `${projectlink}`, `${content.findroot}`. A hardcoded `/site/find/...` breaks on other deployments.

### Page config (the `.xconf`)

- Root element `<page>`.
- `<property name="..." value="..."/>` — page variables usable in Velocity as `$name`.
- `<path-action name="Module.method"/>` — runs a Java module method before render; order matters. Nested child elements pass parameters (`<pageval>`, `<searchtype>`). This is the **only** place server logic is wired from a page — no Groovy, ever (see AGENTS.md).
- `<inner-layout>` — choose the layout wrapper for this page.
- `<style href="..."/>` / `<script src="..."/>` — page-scoped includes; app-wide ones already live in `_site.xconf`.
- `<permission name="...">` — declarative permission checks (userprofile, settingsrole, pagevalue, and/or/or groups).

### Folder defaults (`_site.xconf`)

Read the nearest `_site.xconf` **before writing a page** — it defines what is already loaded (jquery, bootstrap, theme css, all `emedia/*.js`, permissions, base layout). Do not re-include app-wide assets in a page; add only what that specific page needs.

## Conventions for interactive UI

- **AJAX navigation**: class `ajax` on links/forms plus `data-*` attributes: `data-confirm`, `data-targetdiv="applicationcontent"`, `data-closedialog`, `data-updateurl`, `data-url`, `data-oemaxlevel`. The app's JS (`runajax.js`, `emdialog.js`) handles the rest.
- **Forms** POST to a sibling `save.html`; that page's `.xconf` does the work (`DataEditModule.parseMultPart`, `DataEditModule.saveData`, `EntityModule.getEntity`, ...). Keep the edit form dumb; put processing in xconf path-actions.
- **Bootstrap 5.3.8** is loaded app-wide (d-flex, btn, modal classes available). Icons: fontawesome + bootstrap-icons.
- Page-specific CSS goes in `theme/styles/pages/<page>.css`; small screens via `mobile.css`.

## Making changes take effect

| Change | What to do |
|---|---|
| Edit existing `.html` / `.css` | Browser reload only (hard-reload for CSS). No restart. |
| Edit a `.xconf` | Clear the page manager cache (below). |
| **Add or remove** any `.html` or `.xconf` file | Clear the page manager cache (or restart). |
| Java / `plugin.xml` / plugin jars | `bin/restart.sh`, then poll until the server answers. |

Clear the page manager cache (no restart needed):

```bash
curl -H "Authorization: Bearer $(bin/refreshtoken.sh | tail -n1)" \
  http://localhost:8080/openedit/views/filemanager/clearpagemanager.html
```

Verify against the running server (Bearer header auto-logs-in; see AGENTS.md for token rules):

```bash
curl -s -H "Authorization: Bearer $(bin/refreshtoken.sh | tail -n1)" \
  http://localhost:8080/site/find/views/<area>/<page>.html | head -50
```

For a real check, open the page in a browser at `http://localhost:8080/site/find/...` (screenshots per AGENTS.md are the primary verification surface). A 302 to `authentication/nopermissions.html` means the auth header was missing, not that the page is broken.

## Checklist before finishing

- [ ] Page is a fragment (no full `<html>` document) and sits next to its `.xconf`
- [ ] Placed in the right layer: site override vs plugin (and committed in the right git repo)
- [ ] Nearest `_site.xconf` read; no duplicate app-wide includes
- [ ] All user-visible text in `[[...]]`; URLs via page-value variables
- [ ] Nulls use `$!{...}` quiet references
- [ ] Server logic wired as `<path-action>` (Java module), not inline scripting
- [ ] Page manager cache cleared (xconf edit or new/removed file)
- [ ] Verified rendered HTML via curl and viewed in the browser
