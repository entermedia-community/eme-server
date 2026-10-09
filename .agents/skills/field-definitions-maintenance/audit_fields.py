#!/usr/bin/env python3
"""Audit (and optionally normalize) EME field definition XML files.

Implements the rules from SKILL.md in this directory:
  Rule 1: datatype only (no legacy `type`)
  Rule 2: base-entity files structurally identical (fields + attributes; labels kept per table)
  Rule 3: all other attributes preserved, moved to canonical position
  Rule 4: typo fixes (lisstid/editble/multi); oddities reported

Usage:
  audit_fields.py [path ...]            read-only audit (default)
  audit_fields.py --fix --dry-run       preview rewrites
  audit_fields.py --fix                 apply rewrites (refuses on conflicts/unknown attrs)
  audit_fields.py --site-fields [path ...]  read-only report of site-level custom field
                                        definitions (webapp/WEB-INF/data/site/catalog/fields)
                                        vs the plugin level, using the runtime load order (site flat > plugin flat >
                                        site folder > plugin folder, first-wins per id): NEW /
                                        DRIFT / LABELS / DEAD / EMPTY findings plus an IDENTICAL
                                        summary. Never writes.

Paths are relative to the repo root (auto-detected) or absolute; default is
plugins/catalog/html/data/fields plus the master template in html/configuration.
--site-fields defaults to webapp/WEB-INF/data/site/catalog/fields.

Rewrites are surgical: only the attribute string inside <property ...> opening tags
(and, for base files, whole missing property blocks copied from a donor file) is changed.
Labels, CDATA, indentation and everything else are preserved byte-for-byte.
"""
import os
import re
import sys

# ---------------------------------------------------------------- canonical order

ORDERED9 = ["id", "index", "sortable", "filter", "keyword", "editable",
            "multilanguage", "viewtype", "searchcomponent"]
DATATYPE = "datatype"
PRESERVED = ["stored", "internalfield", "required", "deleted", "note", "indextype",
             "searchtype", "isbadge", "defaultoperation", "listid", "listcatalogid",
             "sourcepath", "externalid", "securityfield", "rendertype", "rendermask",
             "autoincrement", "highlight", "aicreationcommand", "aiautocreated",
             "aicreationdescription", "aiautocreateddescription", "writenametoexif",
             "autocreatefromexif", "legacy", "defaultvalue", "multiple", "foreignkeyid",
             "list", "listfilterid", "sort", "listchild", "hint", "format", "date",
             "analyzer", "catalogid", "skipexport"]
CANONICAL = ORDERED9 + [DATATYPE] + PRESERVED
RANK = {name: i for i, name in enumerate(CANONICAL)}

TYPO_FIXES = {"lisstid": "listid", "editble": "editable", "multi": "multiple"}
# reported, never auto-changed (see SKILL.md Rule 4)
KNOWN_ODDITIES = {"externalidXXX", "query", "wordpressfield", "beanname"}

# table-side base files are named baseentity.xml (renamed from
# baseentitytemplate.xml on 2026-10-08); only the master template in
# html/configuration keeps the baseentitytemplate.xml name.
BASE_NAME = "baseentity.xml"
LEGACY_BASE_NAME = "baseentitytemplate.xml"  # report-only, never auto-renamed
MASTER_RELPATH = os.path.join("plugins", "catalog", "html", "configuration",
                              "baseentitytemplate.xml")

FIELDS_DIR = os.path.join("plugins", "catalog", "html", "data", "fields")

# site-level custom field definitions (where the UI saves field edits by default,
# PropertyDetailsArchive.findSavePath() with saveTo="data"). These shadow the
# plugin-level definitions: see SKILL.md "Site-level custom fields".
SITE_FIELDS_DIR = os.path.join("webapp", "WEB-INF", "data", "site", "catalog", "fields")

# canonical base-entity field order (31 fields, 2026-10-08)
BASE_FIELDS = ["id", "name", "description", "keywords", "longcaption", "primaryimage",
               "primarymedia", "entity_date", "entitysourcetype", "sourcepath",
               "archivesourcepath", "viewusers", "viewgroups", "viewerusers",
               "viewergroups", "editorusers", "editorgroups", "securityalwaysvisible",
               "securityenabled", "owner", "emrecordstatus", "rootcategory",
               "enablepublishinggallery", "enablepublishingcarousel", "searchcategory",
               "permissionsupdateddate", "contentcreator", "semantictopicsindexed",
               "semantictopics", "taggedbyllm", "llmerror"]

ATTR_RE = re.compile(r'([A-Za-z_][\w]*)="([^"]*)"')
PROP_TAG_RE = re.compile(r'<property\s+([^>]*?)>')


def find_repo_root(start):
    d = os.path.abspath(start)
    while True:
        if os.path.isdir(os.path.join(d, "plugins", "catalog", "html", "data", "fields")):
            return d
        parent = os.path.dirname(d)
        if parent == d:
            return None
        d = parent


def parse_attrs(attrstr):
    """Return (ordered list of (name, value), list of problems).

    Gaps between attributes may contain whitespace; anything else is a
    problem. Duplicate attribute names are reported (conflicting values are
    a hard conflict)."""
    attrs = []
    problems = []
    pos = 0
    for m in ATTR_RE.finditer(attrstr):
        gap = attrstr[pos:m.start()]
        if gap.strip():
            problems.append(f"unparsed attribute text: {gap.strip()!r}")
        attrs.append((m.group(1), m.group(2)))
        pos = m.end()
    if attrstr[pos:].strip():
        problems.append(f"unparsed attribute text: {attrstr[pos:].strip()!r}")
    seen = {}
    for n, v in attrs:
        if n in seen:
            if seen[n] != v:
                problems.append(f"duplicate attribute {n} with conflicting values")
            else:
                problems.append(f"redundant duplicate attribute {n}")
        else:
            seen[n] = v
    return attrs, problems


def canon_order(attrs):
    """attrs: list of (name, value). Returns (new list, problems)."""
    problems = []
    fixed = []
    for n, v in attrs:
        if n in TYPO_FIXES:
            problems.append(f"typo {n} -> {TYPO_FIXES[n]}")
            n = TYPO_FIXES[n]
        fixed.append((n, v))
    # a typo fix may collide with an existing attribute of the same name
    by_name = {}
    for n, v in fixed:
        by_name.setdefault(n, []).append(v)
    for n, vals in by_name.items():
        if len(vals) < 2:
            continue
        if len(set(vals)) > 1:
            problems.append(f"CONFLICT attribute {n}: values {vals} "
                            f"(after typo normalization) - ask the user")
            return fixed, problems  # do not touch conflicting values
        kept = False
        deduped = []
        for nn, v in fixed:
            if nn == n:
                if not kept:
                    deduped.append((nn, v))
                    kept = True
            else:
                deduped.append((nn, v))
        fixed = deduped
        problems.append(f"redundant duplicate attribute {n} removed")
    if "type" in dict(fixed) and DATATYPE in dict(fixed):
        if dict(fixed)["type"] != dict(fixed)[DATATYPE]:
            problems.append(f"CONFLICT type={dict(fixed)['type']!r} vs "
                            f"datatype={dict(fixed)[DATATYPE]!r}")
            return fixed, problems  # do not touch conflicting values
        fixed = [(n, v) for n, v in fixed if n != "type"]
        problems.append("redundant type removed (same value as datatype)")
    elif "type" in dict(fixed):
        fixed = [(DATATYPE, v) if n == "type" else (n, v) for n, v in fixed]
        problems.append("type -> datatype")
    unknown = [n for n, _ in fixed if n not in RANK]
    for u in unknown:
        if u in KNOWN_ODDITIES:
            problems.append(f"known oddity attribute {u} (reported only)")
        else:
            problems.append(f"UNKNOWN attribute {u!r} - ask the user")
    fixed.sort(key=lambda nv: RANK.get(nv[0], 999))
    return fixed, problems


def render_attrs(attrs):
    return " ".join(f'{n}="{v}"' for n, v in attrs)


# ---------------------------------------------------------------- file scanning

class Prop:
    def __init__(self, text, start, end, tagstart, tagend, attrstart, attrend):
        self.text = text            # full property block text (tag + body)
        self.start = start          # offset of '<property' in file text
        self.end = end              # offset after '>' of the opening tag
        self.tagstart = tagstart    # offset of '<property'
        self.tagend = tagend        # offset just past '>'
        self.attrstart = attrstart  # offset where attribute string begins
        self.attrend = attrend      # offset where attribute string ends
        m = re.match(r'<property\s+([^>]*?)>', text)
        self.attrs, self.problems = parse_attrs(m.group(1))
        self.id = dict(self.attrs).get("id")


def scan_file(path):
    with open(path, "r", encoding="utf-8") as f:
        text = f.read()
    props = []
    for m in PROP_TAG_RE.finditer(text):
        p = Prop(text[m.start():m.end()], m.start(), m.end(), m.start(), m.end(),
                 m.start(1), m.end(1))
        props.append(p)
    return text, props


NAME_BLOCK_RE = re.compile(r"<name\b[^>]*>(.*?)</name>", re.S)
LANG_RE = re.compile(r'<language\s+id="([^"]*)"[^>]*>\s*<!\[CDATA\[(.*?)\]\]>\s*</language>',
                     re.S)


def extract_labels(text, p):
    """{language id: label text} from the <name> block of property p."""
    end = find_block_end(text, p)
    m = NAME_BLOCK_RE.search(text[p.tagend:end])
    if not m:
        return {}
    return {lm.group(1): lm.group(2).strip() for lm in LANG_RE.finditer(m.group(1))}


def top_level_props(text, props):
    """Properties not nested inside another property block."""
    ends = {}
    for p in props:
        if id(p) not in ends:
            ends[id(p)] = find_block_end(text, p)
    tops = []
    for p in props:
        nested = any(q is not p and q.start < p.start < ends[id(q)] for q in props)
        if not nested:
            tops.append(p)
    return tops


def collect_files(root, extra_args):
    if extra_args:
        files = []
        for a in extra_args:
            p = a if os.path.isabs(a) else os.path.join(root, a)
            if os.path.isdir(p):
                for dp, _, fns in os.walk(p):
                    for fn in sorted(fns):
                        if fn.endswith(".xml"):
                            files.append(os.path.relpath(os.path.join(dp, fn), root))
            elif os.path.isfile(p):
                files.append(os.path.relpath(p, root))
            else:
                print(f"error: path not found: {a}")
                sys.exit(2)
        return files
    files = []
    for dp, _, fns in os.walk(os.path.join(root, FIELDS_DIR)):
        for fn in sorted(fns):
            if fn.endswith(".xml"):
                files.append(os.path.relpath(os.path.join(dp, fn), root))
    files.append(MASTER_RELPATH)
    return sorted(set(files))


def is_base(relpath):
    base = os.path.basename(relpath)
    return relpath == MASTER_RELPATH or (base == BASE_NAME and
            relpath.startswith(FIELDS_DIR + os.sep))


# ---------------------------------------------------------------- audit

def audit(root, files, fix, dry_run):
    all_text = {}
    all_props = {}
    for rel in files:
        text, props = scan_file(os.path.join(root, rel))
        all_text[rel] = text
        all_props[rel] = props

    issues = 0
    # legacy table-side base name (pre-2026-10-08): report only; the script
    # never moves files, so the rename to baseentity.xml is manual
    for rel in files:
        if os.path.basename(rel) == LEGACY_BASE_NAME and rel != MASTER_RELPATH:
            print(f"{rel}: legacy base file name; rename to {BASE_NAME}")
            issues += 1
    rewrites = {}   # rel -> list of (start, end, newtext)
    base_attr_union = {}   # field id -> {attr: value}
    base_conflicts = []
    unknown_anywhere = []
    conflict_anywhere = []
    unparsed_anywhere = []

    # pass 1: per-property checks + attribute rewrites for non-base files
    for rel in files:
        for p in all_props[rel]:
            problems = list(p.problems)
            newattrs, order_problems = canon_order(p.attrs)
            problems.extend(order_problems)
            for prob in problems:
                print(f"{rel}: <property id={p.id!r}> {prob}")
                issues += 1
                if prob.startswith("UNKNOWN"):
                    unknown_anywhere.append((rel, p.id, prob))
                if prob.startswith("CONFLICT") or "conflicting values" in prob:
                    conflict_anywhere.append((rel, p.id, prob))
                if prob.startswith("unparsed"):
                    unparsed_anywhere.append((rel, p.id, prob))
            # never rewrite a property whose attribute string does not parse
            # cleanly or carries conflicting duplicate values (the rewrite
            # would destroy data); same-value duplicates are safe to dedupe
            hard = [x for x in p.problems
                    if x.startswith("unparsed") or "conflicting values" in x]
            if hard:
                continue
            # only record a rewrite when the rendered string actually changes
            newstr = render_attrs(newattrs)
            oldstr = all_text[rel][p.attrstart:p.attrend]
            if newstr != oldstr and not any(x.startswith("CONFLICT") for x in problems):
                rewrites.setdefault(rel, []).append((p.attrstart, p.attrend, newstr))

    # pass 2: base-file unification (fields + attribute union)
    base_files = [r for r in files if is_base(r)]
    if base_files:
        # gather per-field attribute union across base files
        field_donors = {}   # id -> rel of first file containing it
        for rel in sorted(base_files):
            seen_ids = set()
            for p in all_props[rel]:
                pid = p.id
                if pid is None:
                    continue
                seen_ids.add(pid)
                attrs = dict(p.attrs)
                if "type" in attrs:  # rule 1: union on datatype
                    if DATATYPE in attrs and attrs["type"] != attrs[DATATYPE]:
                        base_conflicts.append((pid, "datatype",
                                               attrs[DATATYPE], attrs["type"], rel))
                        del attrs["type"]
                    else:
                        attrs[DATATYPE] = attrs.pop("type")
                u = base_attr_union.setdefault(pid, {})
                for k, v in attrs.items():
                    if k == "id":
                        continue
                    if k in u and u[k] != v:
                        base_conflicts.append((pid, k, u[k], v, rel))
                    else:
                        u[k] = v
                field_donors.setdefault(pid, rel)
        # check field sets and order per base file
        for rel in sorted(base_files):
            ids = [p.id for p in all_props[rel]]
            missing = [f for f in BASE_FIELDS if f not in ids]
            extra = [i for i in ids if i not in BASE_FIELDS]
            wrong_order = [i for i in ids if i in BASE_FIELDS] != \
                          [f for f in BASE_FIELDS if f in ids]
            if missing:
                print(f"{rel}: base file missing fields: {missing}")
                issues += 1
            if extra:
                print(f"{rel}: base file has unexpected fields: {extra}")
                issues += 1
            if wrong_order:
                print(f"{rel}: base file field order deviates from canonical")
                issues += 1
        for pid, k, v1, v2, rel in base_conflicts:
            print(f"BASE CONFLICT: field {pid} attribute {k}: "
                  f"{v1!r} (earlier file) vs {v2!r} ({rel}) - ask the user")
            issues += 1

    # pass 3: base-file rewrites (attribute sets to union; insert missing fields)
    if (fix and not conflict_anywhere and not base_conflicts
            and not unknown_anywhere and not unparsed_anywhere):
        for rel in sorted(base_files):
            text = all_text[rel]
            edits = list(rewrites.get(rel, []))
            # attribute-set completion for existing base properties
            for p in all_props[rel]:
                if p.id is None or p.id not in BASE_FIELDS:
                    continue
                target = dict(base_attr_union[p.id])
                have = {n: v for n, v in p.attrs}
                if "type" in have:  # rule 1; conflicts already gate --fix
                    have[DATATYPE] = have.pop("type")
                merged = {}
                for k in [x for x in CANONICAL]:
                    if k == "id":
                        merged["id"] = p.id
                        continue
                    if k in target:
                        merged[k] = target[k]
                # keep any extra (non-canonical) attrs this file has, at the end
                for n, v in have.items():
                    if n not in merged:
                        merged[n] = v
                newstr = " ".join(f'{n}="{v}"' for n, v in merged.items())
                oldstr = text[p.attrstart:p.attrend]
                if newstr != oldstr:
                    # replace existing attr edit for this property if present
                    edits = [(s, e, t) for (s, e, t) in edits
                             if not (s == p.attrstart and e == p.attrend)]
                    edits.append((p.attrstart, p.attrend, newstr))
            # insert missing fields (copy whole block from donor) at their
            # canonical position: after the nearest preceding existing base
            # field's block, or before the nearest following one's tag
            ids = [p.id for p in all_props[rel]]
            base_idx = {f: i for i, f in enumerate(BASE_FIELDS)}
            present = [(base_idx[p.id], p) for p in all_props[rel]
                       if p.id in base_idx]
            for f in BASE_FIELDS:
                if f in ids:
                    continue
                donor = field_donors.get(f)
                if not donor:
                    print(f"{rel}: cannot insert {f}, no donor has it")
                    continue
                dprops = {p.id: p for p in all_props[donor]}
                dp = dprops[f]
                # opening tag rebuilt from the attribute union (so the new field
                # matches every other base file); body/labels copied from donor
                block_end = find_block_end(all_text[donor], dp)
                body = all_text[donor][dp.tagend:block_end]
                indent_start = all_text[donor].rfind("\n", 0, dp.start) + 1
                indent = all_text[donor][indent_start:dp.start]
                merged = {"id": f}
                for k in CANONICAL:
                    if k != "id" and k in base_attr_union[f]:
                        merged[k] = base_attr_union[f][k]
                for n, v in dp.attrs:  # donor extras (non-canonical) at the end
                    if n not in merged and n != "type":
                        merged[n] = v
                block = indent + "<property " + render_attrs(list(merged.items())) \
                        + ">" + body
                fi = base_idx[f]
                prevs = [p for i, p in present if i < fi]
                follows = [p for i, p in present if i > fi]
                if prevs:
                    pos = find_block_end(text, prevs[-1])
                    nl = text.find("\n", pos)
                    if nl != -1 and text[pos:nl].strip() == "":
                        # insert on the next line; trailing whitespace of the
                        # previous line stays with that line
                        edits.append((nl + 1, nl + 1, block.rstrip() + "\n"))
                    else:
                        edits.append((pos, pos, "\n" + block.rstrip() + "\n"))
                elif follows:
                    # the following property's own indent is already in place
                    pos = follows[0].tagstart
                    edits.append((pos, pos, block.lstrip().rstrip() + "\n"))
                else:  # file has no base fields at all; append at end
                    last = max(all_props[rel], key=lambda q: q.end)
                    pos = find_block_end(text, last)
                    edits.append((pos, pos, "\n" + block.rstrip() + "\n"))
                print(f"{rel}: would insert field {f!r} (copied from {donor})")
            if edits:
                rewrites[rel] = apply_edits(text, edits)

    # pass 4: report / write
    changed = 0
    for rel in sorted(rewrites):
        rw = rewrites[rel]
        # pass 3 stores final text for base files; pass 1 leaves edit lists
        newtext = rw if isinstance(rw, str) else apply_edits(all_text[rel], rw)
        if newtext == all_text[rel]:
            continue
        changed += 1
        if dry_run:
            print(f"\n--- {rel} (dry-run, would change) ---")
            import difflib
            for line in difflib.unified_diff(all_text[rel].splitlines(),
                                             newtext.splitlines(), lineterm="", n=1):
                print(line)
        elif fix:
            with open(os.path.join(root, rel), "w", encoding="utf-8") as f:
                f.write(newtext)
            print(f"fixed: {rel}")
    if not fix:
        print(f"\naudit: {issues} issue(s); {changed} file(s) would change under --fix")
    else:
        print(f"\n{'dry-run: ' if dry_run else ''}{changed} file(s) "
              f"{'would change' if dry_run else 'changed'}")
    return 1 if (issues or (fix and changed)) else 0


# ---------------------------------------------------------------- site fields

def _site_match(sub, e):
    e = e.rstrip("/")
    return sub == e or sub.startswith(e + "/") or os.path.dirname(sub) == e


def _table_files(root, table):
    """(rel, text, props) for every field file of a table, in load order:
    site flat, plugin flat, site folder files, plugin folder files
    (PropertyDetailsArchive.getPropertyDetails, first-wins per field id)."""
    out = []
    site_flat = os.path.join(SITE_FIELDS_DIR, table + ".xml")
    if os.path.isfile(os.path.join(root, site_flat)):
        t, pr = scan_file(os.path.join(root, site_flat))
        out.append((site_flat, t, pr))
    plug_flat = os.path.join(FIELDS_DIR, table + ".xml")
    if os.path.isfile(os.path.join(root, plug_flat)):
        t, pr = scan_file(os.path.join(root, plug_flat))
        out.append((plug_flat, t, pr))
    for prefix in (os.path.join(SITE_FIELDS_DIR, table),
                   os.path.join(FIELDS_DIR, table)):
        d = os.path.join(root, prefix)
        if not os.path.isdir(d):
            continue
        for fn in sorted(os.listdir(d)):
            if fn.endswith(".xml"):
                rel = os.path.join(prefix, fn)
                t, pr = scan_file(os.path.join(root, rel))
                out.append((rel, t, pr))
    return out


def audit_site_fields(root, extra):
    """Read-only report of site-level custom field definitions.

    Scans webapp/WEB-INF/data/site/catalog/fields/** and classifies every
    top-level <property> against the plugin-level definitions of the same
    table (load order: site flat > plugin flat > site folder > plugin folder,
    first-wins per field id):
      NEW     - no plugin-level counterpart: move-up candidate
      DRIFT   - in effect at runtime; attribute values differ from the
                plugin-side definition that would take over if the site copy
                were removed (site value vs plugin value)
      LABELS  - attributes match, localized labels differ
      DEAD    - not in effect at runtime: an earlier-loaded file already
                defines the same id (safe to delete, no runtime change)
      EMPTY   - site file with no properties (shell, safe to delete)
    Fields identical to their plugin counterpart are summarized per file.
    Never writes. Exit code: 1 if any finding, 0 if the site directory holds
    no field definitions at all, 2 on usage errors.
    """
    base = os.path.join(root, SITE_FIELDS_DIR)
    if not os.path.isdir(base):
        print(f"site fields directory not found: {SITE_FIELDS_DIR}")
        return 0
    files = []
    for dp, _, fns in os.walk(base):
        for fn in sorted(fns):
            if fn.endswith(".xml"):
                files.append(os.path.relpath(os.path.join(dp, fn), root))
    if extra:
        files = [rel for rel in files
                 if any(_site_match(os.path.relpath(rel, SITE_FIELDS_DIR), e)
                        for e in extra)]
        if not files:
            print(f"error: no site field files match {extra}")
            return 2
    print(f"scanning {len(files)} site field file(s) under {SITE_FIELDS_DIR} "
          f"(repo root {root})")

    table_cache = {}   # table -> [(rel, text, props), ...] in load order
    def table_files(table):
        if table not in table_cache:
            table_cache[table] = _table_files(root, table)
        return table_cache[table]

    def top_map(entries):
        """id -> (rel, text, Prop) for top-level props; first file wins."""
        m = {}
        for rel, text, props in entries:
            for p in top_level_props(text, props):
                if p.id is not None:
                    m.setdefault(p.id, (rel, text, p))
        return m

    issues = 0
    counts = {"NEW": 0, "DRIFT": 0, "LABELS": 0, "DEAD": 0, "EMPTY": 0,
              "IDENTICAL": 0}
    for rel in sorted(files):
        sub = os.path.relpath(rel, SITE_FIELDS_DIR)
        table = sub.split("/")[0] if "/" in sub else os.path.splitext(sub)[0]
        entries = table_files(table)
        # effective source per id: first-wins across all four layers
        eff = {}
        for rel_e, text_e, props_e in entries:
            for p in top_level_props(text_e, props_e):
                if p.id is not None:
                    eff.setdefault(p.id, (rel_e, text_e, p))
        # plugin-side counterpart per id (what takes over if the site copy goes)
        plug_entries = [e for e in entries
                        if e[0].startswith(FIELDS_DIR + os.sep)]
        plug_map = top_map(plug_entries)
        text, props = scan_file(os.path.join(root, rel))
        # canonical-rule checks apply to site files exactly like plugin files
        for p in props:
            problems = list(p.problems)
            _, order_problems = canon_order(p.attrs)
            problems.extend(order_problems)
            for prob in problems:
                print(f"{rel}: <property id={p.id!r}> {prob}")
                issues += 1
        if not props:
            print(f"EMPTY   {rel}: no properties (shell file, safe to delete)")
            counts["EMPTY"] += 1
            continue
        tops = top_level_props(text, props)
        print(f"\n{rel} (table {table}, {len(tops)} field(s)):")
        identical_here = 0
        for p in tops:
            if p.id is None:
                print(f"  NEW     <property> without id - cannot compare")
                counts["NEW"] += 1
                continue
            eref = eff.get(p.id)
            plug = plug_map.get(p.id)
            if eref is not None and eref[0] != rel:
                print(f"  DEAD    {p.id!r}: shadowed by {eref[0]} "
                      f"(not in effect at runtime, safe to delete)")
                counts["DEAD"] += 1
                continue
            site_attrs, _ = canon_order(p.attrs)
            if plug is None:
                print(f"  NEW     {p.id!r}: no plugin-level counterpart "
                      f"- move-up candidate")
                print(f"          site attrs: {render_attrs(site_attrs)}")
                counts["NEW"] += 1
                continue
            prel, ptext, pp = plug
            plug_attrs, _ = canon_order(pp.attrs)
            sd = dict(site_attrs); pd = dict(plug_attrs)
            sd.pop("id", None); pd.pop("id", None)
            diffs = []
            for k in sorted(set(sd) | set(pd)):
                if sd.get(k) != pd.get(k):
                    diffs.append(f"{k}: site={sd.get(k)!r} vs plugin={pd.get(k)!r}")
            if diffs:
                print(f"  DRIFT   {p.id!r}: differs from {prel}:")
                for dline in diffs:
                    print(f"          {dline}")
                counts["DRIFT"] += 1
                continue
            sl = extract_labels(text, p)
            pl = extract_labels(ptext, pp)
            if sl != pl:
                print(f"  LABELS  {p.id!r}: attributes match {prel} but labels "
                      f"differ (site copy is in effect):")
                for lang in sorted(set(sl) | set(pl)):
                    if sl.get(lang) != pl.get(lang):
                        print(f"          {lang}: site={sl.get(lang)!r} "
                              f"vs plugin={pl.get(lang)!r}")
                counts["LABELS"] += 1
                continue
            identical_here += 1
        if identical_here:
            print(f"  IDENTICAL {identical_here} field(s) match their plugin-level "
                  f"counterpart exactly (pure duplication, safe to delete)")
        counts["IDENTICAL"] += identical_here
    total = sum(counts.values())
    print(f"\nsite fields: {total} finding(s) across {len(files)} file(s), "
          f"{issues} canonical-rule issue(s)")
    print(f"  NEW={counts['NEW']} DRIFT={counts['DRIFT']} "
          f"LABELS={counts['LABELS']} DEAD={counts['DEAD']} "
          f"EMPTY={counts['EMPTY']} identical={counts['IDENTICAL']}")
    if total == 0:
        print("site fields: clean - no custom field definitions in the site directory")
    else:
        print("review and move up per SKILL.md 'Site-level custom fields'")
    return 1 if (total or issues) else 0


def find_block_end(text, p):
    """Offset just past the closing </property> of property p (handles nesting)."""
    depth = 0
    i = p.tagend - 1  # position of '>'
    open_re = re.compile(r"<property\b")
    close_re = re.compile(r"</property>")
    while True:
        o = open_re.search(text, i + 1)
        c = close_re.search(text, i + 1)
        if c is None:
            return len(text)
        if o is not None and o.start() < c.start():
            depth += 1
            i = o.end()
        else:
            if depth == 0:
                return c.end()
            depth -= 1
            i = c.end()


def apply_edits(text, edits):
    """edits: list of (start, end, newtext); offsets refer to the ORIGINAL text."""
    edits = sorted(edits, key=lambda e: e[0])
    out = []
    pos = 0
    for s, e, t in edits:
        if s < pos:
            raise RuntimeError("overlapping edits")
        out.append(text[pos:s])
        out.append(t)
        pos = e
    out.append(text[pos:])
    return "".join(out)


def main():
    args = sys.argv[1:]
    fix = "--fix" in args
    dry_run = "--dry-run" in args
    site_fields = "--site-fields" in args
    paths = [a for a in args if not a.startswith("--")]
    root = find_repo_root(os.getcwd())
    if root is None:
        print("error: could not locate repo root (plugins/catalog/html/data/fields)")
        sys.exit(2)
    if site_fields:
        if fix or dry_run:
            print("error: --site-fields is read-only; --fix/--dry-run do not apply")
            sys.exit(2)
        sys.exit(audit_site_fields(root, paths))
    if fix and not dry_run:
        print("NOTE: applying rewrites. Use --dry-run to preview first.")
    files = collect_files(root, paths)
    print(f"scanning {len(files)} files under {root}")
    sys.exit(audit(root, files, fix, dry_run))


if __name__ == "__main__":
    main()
