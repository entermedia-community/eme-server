---
name: reload-list-data
description: Use after editing or adding any XML/CSV file under plugins/catalog/html/data/lists/<table>/ (aiskill, automationstep, endpoint, agenttype, etc.) so the change reaches a running server. Explains restoredata (add/update only) vs deleteall + restoredata (needed when a row was removed), which tables support it, and how to verify row counts.
---

# Skill: reload-list-data

List tables are seeded from files in `plugins/catalog/html/data/lists/<searchtype>/*.xml` (or CSV).
A running server does **not** re-read them on its own. After editing/checking in a list file, load it
into the server by calling the datamanager URLs below. Do this **every time a list file changes**.

Auth and cookie handling are the same as in `update-app-css` (Bearer header on the first call, then the
cookie jar). `<type>` = the folder name under `lists/` (e.g. `aiskill`).

## 1. Added or changed rows: restoredata

```
GET http://localhost:8080/site/find/views/settings/lists/datamanager/list/restoredata.html?searchtype=<type>
```

Adds/updates rows from the list files. It **never removes** anything.

## 2. Removed rows (or renamed ids): deleteall, then restoredata

```
GET http://localhost:8080/site/find/views/settings/lists/datamanager/list/deleteall.html?&searchtype=<type>&origURL=
GET http://localhost:8080/site/find/views/settings/lists/datamanager/list/restoredata.html?searchtype=<type>
```

`deleteall` empties the whole table, so any row that exists only in the database (created in the admin
UI, not in the files) is lost. Say so to the user before doing it on a table that may hold user data.
Always follow it with `restoredata`.

## Which tables support it

Only tables whose field definition in `plugins/catalog/html/data/fields/<type>.xml` does **not** use
`beanname="dataSearcher"` - that searcher type does not read the `lists` folder. `aiskill.xml`
(`beanname="folderSearcher"`) works. Check first:

```bash
grep -n "beanname" plugins/catalog/html/data/fields/<type>.xml
```

## Recipe (curl)

```bash
T=<Bearer token from update-app-css>
B=http://localhost:8080/site/find/views/settings/lists/datamanager/list
curl -s -c jar.txt -o /dev/null -H "Authorization: Bearer $T" http://localhost:8080/site/find/   # login once

# row count for any list table
count(){ curl -s -b jar.txt -X POST -H "Content-Type: application/json" \
  -d '{"page":"1","hitsperpage":"1","query":{"terms":[{"field":"id","operator":"matches","value":"*"}]}}' \
  http://localhost:8080/site/mediadb/services/lists/search/$1 | grep -o '"totalhits" : [0-9]*'; }

count aiskill
curl -s -b jar.txt -o /dev/null "$B/restoredata.html?searchtype=aiskill"          # add/update
# only if a row was removed:
curl -s -b jar.txt -o /dev/null "$B/deleteall.html?&searchtype=aiskill&origURL="
curl -s -b jar.txt -o /dev/null "$B/restoredata.html?searchtype=aiskill"
count aiskill
```

Each call returns HTTP 200 (an HTML page); judge success by the row count, not the body.

Verified 2026-09-25 on `aiskill` (17 files): count 167 -> restoredata (no delete) 167 (idempotent) ->
deleteall 0 -> restoredata 167.

## Notes

- No server restart is needed for list data. (A field-definition change in `data/fields` is different:
  clear the page cache/restart and reindex per `plugins/catalog/AGENTS.md`.)
- Editing the list file and **not** calling restoredata is the usual reason "my change isn't showing".
