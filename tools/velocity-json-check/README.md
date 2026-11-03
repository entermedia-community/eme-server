# velocity-json-check

This tool checks that the Velocity-templated `.json` files under `plugins/*/html/` render to
valid JSON for any data, not just the data you happened to test with.

```bash
tools/velocity-json-check/check.sh                    # every template under plugins/ (about 5 s)
tools/velocity-json-check/check.sh path/to/a.json     # specific files
tools/velocity-json-check/check.sh --dump a.json      # also print the baseline render to stderr
tools/velocity-json-check/check.sh --json a.json      # one JSON result per file
tools/velocity-json-check/check.sh --server           # long-running: one path per stdin line (used by the VS Code extension)
```

Output looks like `file:line:column: severity: message`. The exit code is 1 when any error is
found, which makes it usable in pre-commit and CI.

Requirements:
- JDK 21+
- compiled server classes (`bin/compile.sh`), because the checker loads the real `JsonUtil` from `build/`
- the jars in `plugins/*/lib`

The checker compiles itself into `tools/velocity-json-check/build/` on first run, or whenever the
source changes.

## How it works

1. **The real engine.** Templates are rendered by Velocity 2.3 with the server's settings from
   `VelocityGenerator`, the real `velocitymacros.vm`, and the real `JsonUtil` behind `#jesc`.
2. **Stand-ins for data.** Any reference the template doesn't `#set` resolves to a stub, and so
   does every property or method called on a stub. This works through a custom Velocity
   uberspector and context. Each place that produces a value, such as `$user`,
   `$users.getPageOfHits()` or `$status == "ok"`, is a *site*. A site is keyed by its expression
   text, so every use of the same expression behaves consistently within one render.
3. **Behaviours per site.**

   | Mode | `#if` | `#foreach` | `#ifnull` | Prints as |
   | --- | --- | --- | --- | --- |
   | present | true | 2 items | not null | a number |
   | empty | false | 0 items | null | a number |
   | one | true | 1 item | not null | a number |
   | null | false | 0 items | null | the literal reference |
   | hostile | true | 2 items | not null | `a"b\c` + newline |

   `size()` and `isEmpty()` answer consistently with iteration. Each `==` / `!=` against a
   literal is its own site, so every `#elseif` branch gets exercised.
4. **Renders.** For each template the checker renders:
   - two baselines: everything present, and everything empty
   - for each site, a flip in both directions, plus the one-item case for loops
   - null and hostile renders for values that get printed

   Every output is parsed as strict JSON with Jackson, with duplicate keys detected.
5. **Blame.** A failing flip is reported on the line of the site that was flipped. When a
   baseline itself fails, the checker finds which single flips make it valid and blames those.
6. **Includes.** `$pages.include(...)` is rendered for real. Paths are resolved relative to the
   including file, through the server's `fallbackdirectory` (`_site.xconf`), and with
   `$apphome` / `$applicationid` stripped. Templates that are only ever included are checked
   as JSON fragments (`{..},{..}` or `"a":1,"b":2`). `$context.putPageValue` passes values
   into includes.

## Known limits

- **Data types are unknown.** A bare `"count": $x.count` passes with a numeric stub, even if the
  real value is a string.
- **Branches are flipped one site at a time.** A bug that needs two specific conditions together
  can be missed.
- **Methods named `set*` / `put*` / `add*` / `remove*` / `clear*` are treated as void** (they print nothing).
- **Unresolvable includes are assumed to render `{}`**, and are reported as a warning.

## Tests

```bash
tools/velocity-json-check/test/run.sh            # compare fixtures with test/expected.txt
tools/velocity-json-check/test/run.sh --update   # accept the current output
```
