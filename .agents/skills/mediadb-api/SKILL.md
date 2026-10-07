---
name: mediadb-api
description: Use whenever you need to read or change EME database rows over HTTP — listing tables, finding which tables (modules) matter, inspecting, adding or removing a table's fields, searching rows, loading one row, saving (PUT) or deleting a row, or checking or running scheduled path events (list, last run, run now). Covers the /site/mediadb/services JSON REST API on the local server, its auth header, the search query JSON, and the quirks (lowercase wildcards, case-sensitive exact, PUT to an unknown id creates a new id). Consult this before writing curl calls against mediadb or editing data through the API instead of the XML list files.
---

# MediaDB JSON API

The JSON REST API lives in `plugins/mediadb/html/services/`. Every table ("searchtype") in the
embedded Elasticsearch database is reachable through the same few generic endpoints.

- Base URL: `http://localhost:8080/site/mediadb`
- Every call needs the local admin header (see the root `AGENTS.md`):

```bash
A='Authorization: Bearer adminmd5421c0af185908a6c0c40d50fd5e3f16760d5580bc'
B=http://localhost:8080/site/mediadb
J='Content-Type: application/json'
```

The path is `/site/mediadb/services/...` once. A doubled `/site/mediadb/site/mediadb/services/...`
returns a Tomcat 404 HTML page.

## Workflow

1. Find the table: list all tables, or (better) list modules to see the ones that matter.
2. Read its fields with `listfields` so you know the field ids before searching or saving.
3. Search, then GET/PUT/DELETE individual rows by `id`.

## 1. Discover tables

**All tables** (~400, mostly internal lookup lists):

```bash
curl -s -H "$A" $B/services/settings/datamanager/listtables
```

Returns `results: [{"name": "<searchtype>"}, ...]`.

**Important tables — modules.** The `module` table lists the main entity tables (asset, user,
librarycollection, entitydocument, agentjob, ...). The module `id` is the searchtype to use below.

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/lists/search/module \
  -d '{"page":"1","hitsperpage":"100","query":{"terms":[{"field":"id","operation":"matches","value":"*"}]}}'
```

**Tables enabled for all users — app sections.** Each `appsection` row points at a module through
`toplevelentity.id`:

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/lists/search/appsection \
  -d '{"page":"1","hitsperpage":"20","query":{"terms":[{"field":"id","operation":"matches","value":"*"}]}}'
```

```json
{ "id": "AaCxcUkZ6-nn-EbHpkkT", "toplevelentity": { "id": "entityasset", "name": "Entity Asset" },
  "name": { "en": "Entity Asset" }, "ordering": "3" }
```

## 2. Inspect and change a table's fields

```bash
curl -s -H "$A" "$B/services/settings/datamanager/fields/listfields?searchtype=color"
```

Each result has `id` (the field id used in searches and saves), localized `name`, `editable`,
`index`, and `internalfield`. Only use field ids that appear here.

### Add a field to a table

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/settings/datamanager/fields/addnew \
  -d '{"searchtype":"testtable","newproperty":"testfield","datatype.value":"text"}'
```

- `newproperty` becomes the field id lowercased with spaces removed (`"Due Date"` → `duedate`);
  the original text is the field's English name.
- `datatype.value` sets the type. Datatypes used across `plugins/catalog/html/data/fields`:
  `text`, `list`, `date`, `boolean`, `number`, `double`, `long`, `object`, `nested`, `multi`,
  `textjoin`, `objectarray`, `kwmap`.
- New fields are `editable`, `index`ed and `stored`.
- The response is the table's full field list (same as `listfields`), including the new field.
- The field is written to `webapp/WEB-INF/data/site/catalog/fields/<searchtype>.xml` — the site's
  data folder, not the `plugins/catalog` source files. It takes effect immediately; no restart or
  cache clear.
- Calling `addnew` with an id that already exists (or was removed) just un-deletes that field.

### Remove a field

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/settings/datamanager/fields/remove \
  -d '{"searchtype":"testtable","id":"testfield"}'
```

This is a soft delete: the field stays in the XML with `deleted="true"` and disappears from
`listfields`. Existing row values are not cleared.

## 3. Search

`POST $B/services/lists/search/<searchtype>` with a query body:

```bash
# All colors
curl -s -H "$A" -H "$J" -X POST $B/services/lists/search/color \
  -d '{"page":"1","hitsperpage":"20","query":{"terms":[{"field":"id","operation":"matches","value":"*"}]}}'

# Name starting with "Ca"
curl -s -H "$A" -H "$J" -X POST $B/services/lists/search/color \
  -d '{"page":"1","hitsperpage":"20","query":{"terms":[{"field":"name","operation":"matches","value":"Ca*"}]}}'
```

Response:

```json
{
  "response": { "status": "ok", "totalhits": 1, "hitsperpage": 20, "page": 1, "pages": 1,
                "query": { "friendly": "Name:Ca*", "search": "name:(Ca*)" } },
  "results": [ { "id": "52", "name": "Cadetblue", "colorcode": "#5F9EA0" } ]
}
```

- `page` / `hitsperpage` page through results; check `response.pages` and keep requesting until done.
- Add more objects to `terms` to AND several conditions.
- `response.query.search` shows the query the server actually ran — check it when results look wrong.

Term options (parsed in `JsonUtil.java`, `plugins/finder/code/org/entermediadb/asset/util/`):

| Key | Use |
| --- | --- |
| `field` | Field id from `listfields` |
| `operation` | `matches`, `startswith`, `exact`, `contains`, ... (lowercased by the server) |
| `value` | Single value |
| `values` | Array of values (instead of `value`) |
| `before` / `after` | Date ranges |
| `lowval` / `highval` | Number ranges |

What was verified on the local server (color table, stored value `Cadetblue`):

| Term | Hits | Note |
| --- | --- | --- |
| `name matches Ca*` / `ca*` | 1 / 1 | Trailing wildcard is case-insensitive |
| `name matches cadetblue` / `CADETBLUE` | 1 / 1 | Plain values are case-insensitive |
| `name matches *blue` | 18 | Leading and inner wildcards work... |
| `name matches *Blue` | 0 | ...but only in **lowercase** (they hit the lowercased `name.sort` field) |
| `name matches cadet*lue` | 1 | |
| `name startswith cadet` | 1 | Case-insensitive |
| `name contains adetbl` / `ADETBL` | 1 / 1 | Case-insensitive (server lowercases the value) |
| `name exact Cadetblue` / `cadetblue` | 1 / 0 | `exact` is case-sensitive — match the stored value |
| `id exact 52` | 1 | Use `exact` for ids and list-valued fields |

Rule of thumb: use `contains` or `matches` for text and lowercase any value that has a `*` anywhere
other than the end; use `exact` only when you know the exact stored value.

## 4. Load, save, delete one row

All on `$B/services/lists/data/<searchtype>/<id>`.

```bash
# Load
curl -s -H "$A" $B/services/lists/data/color/52

# Save — only the fields you send are changed; other fields are kept
curl -s -H "$A" -H "$J" -X PUT $B/services/lists/data/color/101 -d '{"name":"DarkKhaki"}'

# Delete
curl -s -H "$A" -X DELETE $B/services/lists/data/color/101
```

Responses: `{"response":{"status":"ok","id":"52"},"data":{...}}`, or `"status":"not found"` for an
unknown id on GET/DELETE.

**PUT to an id that does not exist creates a new row with a generated id** (e.g.
`AaENqTe8FrpiWRChQlNn`), not the id in the URL. Read `response.id` from the reply to learn the real
id. To update, first confirm the row exists with GET or a search.

`POST $B/services/lists/create/<searchtype>` also exists, but its response template
(`plugins/mediadb/html/services/lists/create/layout.json`) currently fails with a Velocity
`#else` parse error even though the row is saved. Prefer PUT.

## 5. Check scheduled events

Path events are the server's background jobs (agent jobs, asset processing, imports, ...). Each one
is an html page under `/site/catalog/events/...` that runs on a timer or when triggered.

**List all events:**

```bash
curl -s -H "$A" $B/services/settings/events/list
```

```json
{
  "response": { "status": "ok", "totalhits": 161, "searchtype": "event" },
  "results": [
    { "name": "Run Agent Jobs", "type": "agentjob",
      "path": "/site/catalog/events/agentjob/runagentjobs.html", "description": "",
      "running": "false", "lastrun": "Oct 5, 2026, 1:58:54 PM", "period": "900000", "enabled": "true" }
  ]
}
```

- `period` is in milliseconds (`900000` = 15 min); `0` means it only runs when triggered.
- `running` / `enabled` are the strings `"true"` / `"false"`.
- `lastrun` is a display date (empty if it never ran) and may contain a narrow no-break space
  (` `) before AM/PM.
- `path` is the id for the per-event calls below.

Quick summary (running events, enabled count):

```bash
curl -s -H "$A" $B/services/settings/events/list | python3 -c '
import json,sys; r=json.load(sys.stdin)["results"]
print("running:", [e["name"] for e in r if e["running"]=="true"])
print("enabled:", sum(e["enabled"]=="true" for e in r), "of", len(r))'
```

**One event's details** — POST its `path` as `eventpath`:

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/settings/events/event/details \
  -d '{"eventpath":"/site/catalog/events/agentjob/runagentjobs.html"}'
```

A GET with a query parameter works too:
`$B/services/settings/events/event/details.json?eventpath=<path>`.

```json
{ "response": { "eventpath": "/site/catalog/events/agentjob/runagentjobs.html" },
  "data": { "name": "Run Agent Jobs", "period": "15m", "enabled": "true",
            "lastrun": "2026-10-05T19:58:54.701Z", "running": "false" } }
```

Here `period` is formatted (`15m`) and `lastrun` is ISO-8601 UTC, which makes it easier to compare
than the list output.

**Run an event now** — POST its `path` as `runpath`:

```bash
curl -s -H "$A" -H "$J" -X POST $B/services/settings/events/event/run \
  -d '{"runpath":"/site/catalog/events/tests/testevent.html"}'
```

- Always returns `{"response":{"status":"ok"}}`, **even for a path that doesn't exist**. Take the
  path from the list call, and confirm the run with the details call: `lastrun` should move to just
  now (`running` is `"true"` while a long event is still going).
- Event paths depend on the catalog. On this local server they are `/site/catalog/events/...`;
  other installs may use e.g. `/media/catalogs/public/events/...`. Don't copy a path from another
  server.
- Running an event has real side effects (agent jobs, imports, emails). Only run events the user
  asked for. `/site/catalog/events/tests/testevent.html` is safe to use for testing the call.

Other calls in `plugins/mediadb/html/services/settings/events/event/` (not needed just to check
status):

- `save.json` and `remove.json` change or delete the event definition. Don't use them for checking.
- `log.txt?eventpath=<path>` currently returns its template text
  (`$!pathevent.getLastOutputHtml()`) unrendered, so it can't be used to read the last output.

All event calls need the `api-manage-events` user profile property (the admin token has it).

## Notes

- Rows changed through the API live only in the database. Rows seeded from
  `plugins/catalog/html/data/lists/<table>/*.xml` are reloaded from those files with the
  `reload-list-data` skill, which can overwrite or (after `deleteall`) remove API edits.
- Permissions are per method (`api-search-data`, `api-load-data`, `api-update-data`,
  `api-delete-data` user profile properties, see `services/lists/*/_site.xconf`). The admin bearer
  token has all of them.
- Clean up any test rows you create.
