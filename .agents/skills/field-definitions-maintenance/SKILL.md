---
name: field-definitions-maintenance
description: Use when maintaining, matching, or auditing the table/field definition XML under plugins/catalog/html/data/fields (and the master template plugins/catalog/html/configuration/baseentitytemplate.xml) — e.g. "match all field definitions", "normalize property attributes", "make baseentity.xml identical across tables", "convert type to datatype", or before/after adding fields to a table. Also use for site-level custom fields: scanning webapp/WEB-INF/data/site/catalog/fields, promoting (moving up) site field definitions into the plugin-level base definitions, or checking which site fields shadow/drift from base ("check site fields", "move these fields up", "which fields are defined at site level"). Defines the canonical attribute order, the base-entity identity rule, the site→plugin merge semantics, and how to audit with audit_fields.py.
---

# Skill: field-definitions-maintenance

Field definitions live in `plugins/catalog/html/data/fields/`:

- One `<table>.xml` per plain table, or a `<table>/` folder for tables with extra field groups
  (e.g. `asset/dam.xml`, `asset/ai.xml`). Folder files extend the same table.
- Root element is `<properties beanname="...">` (searcher type). Exceptions that must be
  preserved as-is: 10 files under `asset/` use root `<page>`, and `asset/face.xml` uses `<fields>`.
- Each `<property id="...">` defines one field. Attributes describe behavior; a `<name>` child
  holds localized labels (`<language id="en|de|fr|es"><![CDATA[...]]></language>`).
- Properties can be **nested** (object/nested datatypes, e.g. `asset/dam.xml` `clips`,
  `goaltask.xml` `taskroles`) — every rule below applies recursively to nested properties too.
- A property element may carry legacy inline text (e.g. `>All Fields ` before `<name>`). Never
  touch it; only attributes are normalized.

As of 2026-10-08: 375 XML files, 3,309 `<property>` elements (3,339 including the 30 in the
master template), 40 table folders.

## Canonical attribute order

Attributes on every `<property>` must appear in this order (attributes not present are simply
absent — do **not** invent values):

1. `id`
2. `datatype`
3. `viewtype`
4. `index`
5. `stored`
6. `filter`
7. `keyword`
8. `editable`
9. `multilanguage`
10. `required`
11. then the preserved attributes, in this fixed order:
    `listid`, `sorted`, `internalfield`, `searchcomponent`, `deleted`, `note`, `indextype`, `searchtype`,
    `isbadge`, `defaultoperation`, `listcatalogid`, `sourcepath`, `externalid`,
    `securityfield`, `rendertype`, `rendermask`, `autoincrement`, `highlight`,
    `aicreationcommand`, `aiautocreated`, `aicreationdescription`, `aiautocreateddescription`,
    `writenametoexif`, `autocreatefromexif`, `legacy`, `defaultvalue`, `multiple`,
    `foreignkeyid`, `list`, `listfilterid`, `sort`, `listchild`, `hint`, `format`, `date`,
    `analyzer`, `catalogid`, `skipexport`

    (`skipexport="true"` is functional — it excludes a field from CSV/asset exports, read by
    `BaseExporter.java` and the export groovy scripts. Added to the canonical list 2026-10-08;
    seen once: `category/categorytree.xml`, field `parents`.)

An attribute that is not in this list at all: **stop and ask the user** what to do with it. Do
not guess, do not delete.

## Rule 1 — datatype only (no legacy type)

- `type` is legacy for `datatype`. Every property must use **only** `datatype`.
- Migration: rename `type` → `datatype`, place it at position 10 in the canonical order.
- If both are present with the **same** value, just drop `type`.
- If they **conflict**, do not pick one silently — report and ask. Runtime note:
  `PropertyDetail.getDataType()` reads `datatype` first and only falls back to `type` when
  `datatype` is absent, so where both exist the effective value is already `datatype`.
- Resolved 2026-10-08 (user decision): `userpost.xml` / `userupload.xml`, field `reportedby`
  (`type="list"` vs `datatype="multi"`) → keep `datatype="multi"`, drop the dead `type`.

## Rule 2 — base-entity files must be structurally identical

The shared base definition exists under one table-side name plus the master template:

- `<table>/baseentity.xml` (one per table that uses the base entity)
- Master template `plugins/catalog/html/configuration/baseentitytemplate.xml` — **this file
  keeps the `baseentitytemplate.xml` name; never rename it.**

On 2026-10-08 all 17 table-side copies named `baseentitytemplate.xml` were renamed to
`baseentity.xml`. Why this is safe: the loader (`PropertyDetailsArchive.getPropertyDetails`)
scans **all** `.xml` files in each table folder regardless of name, and
`WorkspaceManager.saveModule()` always copies the master template to `<table>/baseentity.xml`
when an entity module is saved — `baseentity.xml` is the canonical table-side name. A leftover
table-side `baseentitytemplate.xml` is reported by audit_fields.py (report-only; the script
never moves files).

Tables using it (2026-10-08, 22 folder copies + master): aitutorial, componentrenderversion,
contentcreator, drupalcontent, emedialightbox, emeprofile, entityarticle, entityasset,
entityassetpage, entityassetworkflow, entitycompany, entitydocument, entitydocumentpage,
entitylocation, entityperson, entitytopic, entitytutorial, entitywebcontent, librarycollection,
modulesearch, searchcategory, userpost. (`entityquestion/base.xml` is unrelated quiz data —
never touch it.)

Identity requirements:

1. **Same 31 fields, same order** in every base file and in the master template:
   `id, name, description, keywords, longcaption, primaryimage, primarymedia, entity_date,
   entitysourcetype, sourcepath, archivesourcepath, viewusers, viewgroups, viewerusers,
   viewergroups, editorusers, editorgroups, securityalwaysvisible, securityenabled, owner,
   emrecordstatus, rootcategory, enablepublishinggallery, enablepublishingcarousel,
   searchcategory, permissionsupdateddate, contentcreator, semantictopicsindexed, semantictopics,
   taggedbyllm, llmerror`
   (Before 2026-10-08 only entityasset/entityassetpage/entitywebcontent had
   `semantictopicsindexed`; it now belongs to all base files, placed right before
   `semantictopics`.)
2. **Same attribute set and same values** for each field id across all base files (union of
   what exists anywhere; attributes in canonical order). Where the same attribute has different
   values in different tables, report and ask — do not silently pick one.
3. **Localized `<name>` labels may differ per table** — keep each table's own labels. Identity is
   structural (fields + attributes), not byte-for-byte.
4. When a base file gains/loses a field or an attribute value changes, update the master template
   in `html/configuration/` in the same change so it stays the canonical source.

## Rule 3 — other attributes: preserve values, canonical position

Attributes outside the first 10 (stored, internalfield, listid, note, required, ...) are
functional/table-specific: **keep their values exactly**, only move them to their fixed canonical
position (item 11 in the order above). Never add, remove, or revalue them.

## Rule 4 — known typos to fix, known oddities to report

Fix automatically (obvious typos):

| wrong        | right     | seen in                                        |
| ------------ | --------- | ---------------------------------------------- |
| `lisstid`    | `listid`  | `transaction.xml` (`collectionid`)             |
| `editble`    | `editable`| `videotrack.xml` (`length`)                    |
| `multi`      | `multiple`| `collectiveproduct/collectiveproductrental.xml`(`blockeddates`) |

Typo-fix collision rule: if fixing a typo would create a duplicate attribute name, compare
values — identical values are deduplicated; **different values are a conflict: stop and ask**.
Resolved 2026-10-08 (user decision): `videotrack.xml` `length` had `editble="true"` *and*
`editable="false"` → keep `editable="false"`, drop the dead typo'd attribute (the field stays
read-only; no code ever read `editble`).

Report to the user, never auto-change (may be load-bearing):

- `asset/dam.xml`: `externalidXXX="assetcategory.categoryid"` on `category`, plus `query=` and
  `wordpressfield=` attributes; `beanname="folderSearcher"` on a **property** element in
  `modulepermission.xml` (beanname normally belongs to the root `<properties>`).

## Audit / normalize

The companion script `audit_fields.py` (next to this file) implements all rules:

```bash
# read-only audit of everything (default): prints violations per rule, exit 1 if any
python3 .agents/skills/field-definitions-maintenance/audit_fields.py

# single table or folder
python3 .agents/skills/field-definitions-maintenance/audit_fields.py asset userpost.xml

# preview the exact rewrites without touching files
python3 .agents/skills/field-definitions-maintenance/audit_fields.py --fix --dry-run

# apply rewrites (only run after the user has reviewed the dry-run output)
python3 .agents/skills/field-definitions-maintenance/audit_fields.py --fix
```

`--fix` applies: canonical attribute ordering, `type`→`datatype`, typo fixes from Rule 4, and
base-file field/attribute unification (labels are never touched). It refuses to run if it finds a
value conflict or an unknown attribute — resolve those first.

## Site-level custom fields (promotion)

A website can define its own fields in `webapp/WEB-INF/data/site/catalog/fields/` with the same
layout as the plugin side: a flat `<table>.xml`, and/or a `<table>/` folder (commonly a copy of
`baseentity.xml`). These are **not** a separate namespace — they merge into the same table's field
set, and where both sides define the same field id, the site copy wins.

### Merge semantics (load order)

`PropertyDetailsArchive.getPropertyDetails()` loads a table's fields in this order, first-wins per
field id (`loadDetails()` skips any id already seen):

1. **Site flat** — `webapp/WEB-INF/data/site/catalog/fields/<table>.xml`
2. **Plugin flat** — `plugins/catalog/html/data/fields/<table>.xml`
3. **Site folder** — `webapp/WEB-INF/data/site/catalog/fields/<table>/*.xml` (sorted)
4. **Plugin folder** — `plugins/catalog/html/data/fields/<table>/*.xml` (sorted)

Consequences:

- A site flat file shadows the plugin flat file field-by-field, and both shadow every folder file.
- A site *folder* copy of a field that the plugin *flat* file also defines is **dead code** — the
  plugin flat loads first, so the site copy never takes effect at runtime (e.g. the site
  `baseentity.xml` copies' `longcaption`/`primarymedia` for entityassetpage, `keywords` for
  librarycollection). Deleting such a field changes nothing at runtime.
- Site copies silently shadow base definitions: a plugin-level fix (e.g. setting
  `multilanguage="true"` on `name`) has no runtime effect while the site copy still carries the old
  value.

### Audit mode: `--site-fields`

```bash
# read-only report of every site-level field definition vs the plugin level (exit 1 if any)
python3 .agents/skills/field-definitions-maintenance/audit_fields.py --site-fields

# restricted to one table or file (path relative to the site fields dir, e.g. "userprofile.xml")
python3 .agents/skills/field-definitions-maintenance/audit_fields.py --site-fields userprofile.xml
```

The mode is **read-only** — it never writes. It classifies every top-level `<property>` in the site
files against the plugin level, using the load order above to decide what is actually in effect:

| category  | meaning                                                                                              | action                                    |
| --------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------- |
| `NEW`     | no plugin-level counterpart — a genuine site-only field                                               | move-up candidate (see workflow)          |
| `DRIFT`   | in effect at runtime; attribute values differ from the plugin-side definition that would take over if the site copy were removed | diff each attribute, let the user decide what moves up |
| `LABELS`  | attributes match, localized `<name>` labels differ (site copy is in effect)                           | keep the site label (per-table labels are legitimate) or move it up — user decides |
| `DEAD`    | not in effect at runtime: an earlier-loaded file already defines the id                               | safe to delete, no runtime change         |
| `EMPTY`   | site file with no properties (shell)                                                                  | safe to delete                            |
| `IDENTICAL` | field is byte-for-byte equivalent to its plugin counterpart (summarized per file)                 | pure duplication — safe to delete         |

Canonical-rule violations found *inside* site properties (unknown attributes, `type` vs
`datatype`, typos, order problems) are printed the same way as in the default audit and counted
separately. Exit code: 1 if any finding or issue, 0 if the site directory holds no field
definitions at all.

Known state (2026-10-09, after `--fix` applied to the real corpus): 13 site files —
7 empty shells; 6 site `baseentity.xml` copies (contentcreator, entityasset, entityassetpage,
entitycompany, entitylocation, librarycollection) now attribute-identical to the plugin side
(all base files agree on `name` `multilanguage="false"` here), carrying only per-table label
customizations; full `--site-fields` summary: NEW=0 DRIFT=0 LABELS=21 DEAD=3 EMPTY=7
identical=162. The site `userprofile.xml` 4 NEW fields
(`librarycollection_entitytabopen`, `assetopentab`, `lastcatalog`, `assetdialogtreestatus`) were
promoted to `plugins/catalog/html/data/fields/userprofile.xml` and the site file deleted.

### Move-up workflow (per field, user decides)

1. Run `--site-fields` and review the report. For each `NEW`/`DRIFT` field, present the diff to
   the user and get a decision: move the whole site value up, keep the plugin value (drop the site
   copy), or keep both as-is. Never merge silently — every attribute that differs is a judgment
   call.
2. Insert the field into the plugin-level definition:
   - `NEW` field → add it to the appropriate plugin file for the table (flat `<table>.xml`, or a
     folder file if it belongs to an existing group). Place it at a sensible position and order its
     attributes canonically (Rules 1–4 apply — site files must obey the same rules as plugin files).
   - `DRIFT` field → update the attribute value(s) the user chose in the plugin file, keeping
     canonical order.
   - If the field belongs to a base-entity table and the change is structural (new field, or an
     attribute that should hold for all tables), apply it to **all** base files + the master
     template per Rule 2 — otherwise the next table created from the template will miss it.
3. Remove the site copy: delete the `<property>` block from the site file; if the site file is now
   empty, delete the file (and its folder if empty). Deleting a `DEAD`/`IDENTICAL` field or an
   empty shell is safe without any user decision beyond confirming the report.
4. Re-audit: `--site-fields` should show the field gone; the default audit must still pass.
5. Follow "After changing field definitions" below: clear the page manager cache, and reindex if
   an ES-affecting attribute (`index`, `filter`, `keyword`, `datatype`, `indextype`) changed.

## After changing field definitions

Field XML is read through the xconf cache; changes do **not** apply on page reload:

1. Clear the page cache (see `plugins/catalog/AGENTS.md` / root AGENTS.md for the
   `clearpagemanager.html` call with the Bearer header). No server restart needed for XML-only
   changes.
2. If a field's searchability or type changed (`index`, `filter`, `keyword`, `datatype`,
   `indextype`, ...), the Elasticsearch mapping is derived from these definitions — reindex the
   affected table and say so to the user.
3. This skill only edits XML under `data/fields` and `configuration`. It never touches Java code,
   list data, or views; if a change implies list rows (`listid`) or views, hand off to the
   `reload-list-data` skill.

## Verified

2026-10-08 (joint testing with the user):

- Full corpus scanned: 375 files / 3,309 properties (+30 in the master template = 3,339).
  Attribute inventory, base-file diffs (missing `filter`/`sortable`/`keyword` on id, description,
  keywords, entity_date, emrecordstatus, contentcreator, taggedbyllm, llmerror; label differences
  documented), and the `type`/`datatype` conflicts above were established in this audit.
- Pre-fix audit: **1,463 issues, 0 conflicts, 0 unknown attributes, 366 files would change**
  (929 `type`→`datatype` renames, 507 redundant `type` drops, 2 typos `lisstid`/`multi`,
  5 report-only oddities, 20 base files missing `semantictopicsindexed`).
- User decisions recorded above: `reportedby` keeps `datatype="multi"`; `videotrack` `length`
  keeps `editable="false"` (dead `editble` dropped); `skipexport` added to the canonical list.
- Bugs found and fixed in `audit_fields.py` during testing: redundant-`type` removals are now
  reported; `parse_attrs` gap check is whitespace-tolerant and parse problems surface in pass 1
  (unparsed text / conflicting duplicates block that property's rewrite); typo-fix collisions are
  CONFLICTs, not silent dedupes; missing base fields insert at their canonical position (not
  appended after the last property); the `type`→`datatype` fallback in the base attribute-union
  and per-file merge was dead code (attribute popped before the check) — fixed, otherwise
  datatype-only fields like `entitysourcetype` lost their datatype; pass 4 compared raw edit
  lists against file text.
- End-to-end sandbox test: `--fix` applied to a copy of the corpus, then re-audited → **0 files
  would change**, only the 5 report-only oddities remain; all 376 XMLs still parse; all 23 base
  files (22 table copies + master) are structurally identical — same 31 fields in canonical order
  with an identical attribute set per field.
- Note: two commented-out `<property>` blocks in `collectiveinvoice*.xml` are also normalized
  (attributes reordered inside the comment; content preserved). Harmless, but expected in diffs.

2026-10-09 (applied to the real corpus):

- `--fix` run on the working tree: **367 files changed** (attribute ordering, `type`→`datatype`,
  redundant `type` drops), then the 3 recorded conflicts resolved by hand per the decisions above
  (`userpost`/`userupload` `reportedby` → `datatype="multi"`; `videotrack` `length` →
  `editable="false"`, dead `editble` dropped).
- The 17 legacy table-side `baseentitytemplate.xml` files were renamed to `baseentity.xml`
  (manual — the script only reports renames).
- Gotcha: pass 3 (base-file unification + missing-field insertion) is gated on
  `not conflict_anywhere`, so while the 3 CONFLICTs above were unresolved the first `--fix`
  silently skipped all base files. After resolving them, a second `--fix` unified **20 base
  files** (attribute union + `semantictopicsindexed` inserted into the 19 tables and the master
  template that lacked it). If `--fix` reports CONFLICTs, expect to run it again after resolving.
- Final state: full audit → only the 5 report-only oddities; all 23 base files structurally
  identical (31 fields each); site `userprofile.xml` promoted + deleted (see Known state).
