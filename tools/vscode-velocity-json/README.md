# Velocity JSON (EME): VS Code language support

Support for the `.json` files under `plugins/*/html/` that are really Apache Velocity
templates (about 320 files, such as `services/module/emesearch/search.json`). This extension
is for eme-server only and isn't published. It provides:

1. **Syntax highlighting** for Velocity and JSON together.
2. **JSON validity checks**: every template is rendered with stub data, and you're told when
   some combination of data would produce invalid JSON. This is done by
   [`tools/velocity-json-check`](../velocity-json-check/README.md).

## Highlighting

Opening these files as plain JSON paints them red, because `#if(...)`, `#jesc(...)` and
`$refs` aren't valid JSON. This extension adds a `velocity-json` language:

- Velocity is the base layer: directives, `#ifnotnull` / `#ifnull` blocks, macros from
  `plugins/system/html/display/velocitymacros.vm` (`#jesc`, `#jrender`, `#jsonpartial`, ...),
  `$ref` / `$!ref` / `${ref}` chains with method calls, `##` and `#* *#` comments, and `#[[ ]]#`.
- JSON is tokenized with flat, stateless patterns (keys, strings, numbers, punctuation), so a
  directive in a key, value or comma position never breaks the highlighting of the rest of the file.
- References inside JSON strings are highlighted (`"userid": "$user.id"`).
- Folding on `#if` / `#foreach` / `#ifnotnull` / `#macro` ... `#end`, `##` comment toggling,
  and auto-indent.

The language is scoped by a `files.associations` entry in the workspace you open
(`eme-server/workspace.code-workspace`, or any multi-root `.code-workspace` that includes eme-server):

```jsonc
"files.associations": { "**/plugins/*/html/**/*.json": "velocity-json" }
```

## JSON validity checks

Each time you open or save a template, its rendered JSON is checked, and problems appear as
squiggles and in the Problems panel:

| Severity | Meaning | Example |
| --- | --- | --- |
| Error | Some data makes the output invalid JSON. The message names the value and the case. | ``Invalid JSON when `$categories` is false/empty/missing: Unexpected character (']')`` |
| Warning | A value printed bare in JSON would break the output if it were null. Also used for includes that couldn't be resolved, and for output that is empty. | ``Invalid JSON when `$event.goingcount` is null`` |
| Info | A value is inserted into a JSON string without `#jesc`, so a `"`, `\` or newline in the data would break it. | ``"userid": "$user.id"`` |

- **Command:** **Velocity JSON: Check All Templates** checks every template into the Problems panel.
- **Settings:**
  - `velocityJson.renderCheck.enabled` (default on)
  - `velocityJson.renderCheck.showUnescapedHints` (default on)
- **Requirements:** the server's classes must be compiled (`bin/compile.sh`), and a JDK 21+ must
  be available (`JAVA_HOME` or `java` on `PATH`).
- **Cost:** a background Java process starts once, in about 1 s. After that, each check takes
  milliseconds and never blocks typing. Checks read the file from disk, so results reflect the
  last save.

## Install (once per machine, and again after updates)

1. In VS Code, open the Command Palette and run **Developer: Install Extension from Location...**
2. Select this folder (`tools/vscode-velocity-json`).
3. Reload the window.

If you'd rather install a packaged copy: run `npm run package` (this fetches `@vscode/vsce`
from npm), then `code --install-extension velocity-json.vsix`.

If the extension isn't installed, the association falls back to plain text, so you lose
nothing compared with plain JSON.

## Test

```bash
tools/vscode-velocity-json/test/run.sh             # grammar: every template under plugins/ + test/edge-cases.json
tools/velocity-json-check/test/run.sh              # checker: fixtures vs test/expected.txt
```

The grammar test uses the TextMate engine bundled inside VS Code (no `npm install`). It flags
a file when text is left with no scope, or when a begin/end rule is still open at end of file.
That usually means the template itself is broken: an unclosed `(`, or `end` written without the `#`.

## Roadmap

### Phase 2: static linter

The render check (phase 3, done) already covers unbalanced blocks and unclosed parentheses
(as Velocity parse errors), unescaped string values, bare values that break when null,
duplicate keys and non-JSON output. What's left are rules that need the source text rather
than the rendered output:

| Rule | Example |
| --- | --- |
| Directive word missing its `#` | `lists/create/layout.json:35`: `end` |
| Unknown `#directive` / macro (checked against `velocitymacros.vm` and any `#macro` in the file) | `#jsec(...)` |
| `#else` / `#elseif` directly inside `#ifnull` / `#ifnotnull`, which don't support them | use the opposite directive instead |
| `#set($x = expr)` inside a loop with a possibly-null right side and no reset first (a null keeps the previous loop pass's value) | the pattern at `emesearch/search.json:93` does it correctly |

Also planned: hover docs for EME macros, go-to-definition into `velocitymacros.vm`, and
document symbols for top-level keys.
