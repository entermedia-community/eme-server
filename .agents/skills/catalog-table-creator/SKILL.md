---
name: catalog-table-creator
description: Use this skill whenever the user wants to create, add, or scaffold a new database/catalog table (or "entity") inside the plugins/catalog/html/data structure of this project. This includes requests like "create a new table called X", "add a catalog entity for X", "scaffold a new entity table", or "add a field/property of type list to table X". Also use it when the user wants to add a new list-type field to an existing table, since that requires creating a matching list xml file. Always consult this skill before hand-writing any XML under plugins/catalog/html/data/{fields,lists,views} or plugins/catalog/html/configuration, since the folder/file layout and linking conventions are project-specific and easy to get wrong.
---

# Catalog Table Creator

Creates new database/catalog tables for this project's catalog plugin, following the
project's folder conventions under `plugins/catalog/html/data`.

## Relevant folders

```
plugins/catalog/html/
├── configuration/
│   └── baseentitytemplate.xml      <- base template for "entity*" tables
└── data/
    ├── fields/                     <- table/property definitions live here
    ├── lists/                      <- list-type field value files live here
    └── views/                      <- (not handled by this skill)
```

## Step 1: Determine table type from the table name

- **Name starts with `entity`** (e.g. `entityfoo`, `entityCustomer`) → this is an **entity table**.
  Go to "Creating an entity table" below.
- **Any other name** (e.g. `loremipsum`) → this is a **plain table**.
  Go to "Creating a plain table" below.

## Step 2a: Creating an entity table

1. Create a folder named after the table inside `fields/`:
   ```
   plugins/catalog/html/data/fields/<tablename>/
   ```
2. Inside that folder, create a symlink named `baseentitytemplate.xml` pointing at the
   project's base template:
   ```bash
   ln -s ../../../configuration/baseentitytemplate.xml \
     plugins/catalog/html/data/fields/<tablename>/baseentitytemplate.xml
   ```
   Adjust the relative path (`../../../...`) to match the actual depth from the repo root
   you're working in — always verify with `ls -l` after creating the symlink that it
   resolves correctly to `plugins/catalog/html/configuration/baseentitytemplate.xml`.
3. If the user supplied any additional fields beyond the base template, write them into a
   second xml file in the same folder, named generically:
   ```
   plugins/catalog/html/data/fields/<tablename>/fields.xml
   ```
   using the property XML format described in "Field/property XML format" below.
   If no additional fields were given, skip this file — the base template alone is enough.

## Step 2b: Creating a plain table

Create a single xml file named after the table directly in `fields/`:

```
plugins/catalog/html/data/fields/<tablename>.xml
```

containing all the properties the user specified, using the format below.

## Field/property XML format

```xml
<?xml version="1.0" encoding="UTF-8"?>

<properties>
  <property id="fieldone" index="true" stored="true" editable="true">
    <name>
      <language id="en">Field One</language>
    </name>
  </property>

  <property id="fieldtwo" index="true" stored="true" editable="true" type="list" listid="fieldtwo">
    <name>
      <language id="en">Field Two</language>
    </name>
  </property>
</properties>
```

Notes:

- `id` is the machine field name (lowercase, no spaces) — derive it from the field name the
  user gives (e.g. "Field One" → `fieldone`).
- `<name><language id="en">...</language></name>` holds the human-readable label — use the
  user's wording, title-cased.
- Default attributes to include unless the user says otherwise: `index="true"`,
  `stored="true"`, `editable="true"`.
- Only add optional attributes (`viewtype`, `datatype`, `searchcomponent`, etc.) if the user
  explicitly specifies them. Don't invent values for attributes that weren't requested.
- For entity tables, only put the _additional_ fields the user asked for in `fields.xml` —
  don't repeat anything already provided by `baseentitytemplate.xml`.

## Handling `type="list"` fields

Whenever a property has `type="list"`:

1. Determine the list id: use the `listid` attribute if given, otherwise fall back to the
   `id` of the property.
2. Create a corresponding list file:
   ```
   plugins/catalog/html/data/lists/<listid>.xml
   ```
3. Populate it with the option values the user provides, using this format:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>

   <properties>
     <property id="usa">United States</property>
     <property id="afg">Afghanistan</property>
   </properties>
   ```

   - `id` should be a short stable code for the option (lowercase; reuse the user's codes if
     given, otherwise derive a sensible short code from the label).
   - The element text is the human-readable label.
   - Note: despite the property example in the original spec showing a typo
     (`</country>` instead of `</property>`), always close tags correctly as `</property>`.

4. If the user didn't provide any list values yet, still create the list xml file with an
   empty `<properties>` element so the link from the field to the list is valid, and tell the
   user they can add options later.
5. If a list with that id already exists, ask the user whether to append new options or
   leave it untouched — don't silently overwrite an existing list file.

## Workflow checklist

When asked to create a new table:

1. Confirm the table name and the list of fields/properties (name, type, and any special
   attributes) with the user if not already fully specified.
2. Determine entity vs. plain table from the name prefix.
3. Create the folder/file structure as described above.
4. For every `type="list"` field, also create/update the matching file in `lists/`.
5. Show the user the final file tree and the contents of each created/modified file.

## Example: plain table, no list fields

User: "Create a table `loremipsum` with fields `title` and `description`."

Creates `plugins/catalog/html/data/fields/loremipsum.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>

<properties>
  <property id="title" index="true" stored="true" editable="true">
    <name>
      <language id="en">Title</language>
    </name>
  </property>

  <property id="description" index="true" stored="true" editable="true">
    <name>
      <language id="en">Description</language>
    </name>
  </property>
</properties>
```

## Example: entity table with a list field

User: "Create an entity table `entitycustomer` with an extra field `country` (list type)
with options USA and Afghanistan."

1. `plugins/catalog/html/data/fields/entitycustomer/baseentitytemplate.xml`
   → symlink to `plugins/catalog/html/configuration/baseentitytemplate.xml`
2. `plugins/catalog/html/data/fields/entitycustomer/fields.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>

   <properties>
     <property id="country" index="true" stored="true" editable="true" type="list" listid="country">
       <name>
         <language id="en">Country</language>
       </name>
     </property>
   </properties>
   ```

3. `plugins/catalog/html/data/lists/country.xml`:

   ```xml
   <?xml version="1.0" encoding="UTF-8"?>

   <properties>
     <property id="usa">United States</property>
     <property id="afg">Afghanistan</property>
   </properties>
   ```
