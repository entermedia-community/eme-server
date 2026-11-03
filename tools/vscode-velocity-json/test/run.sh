#!/usr/bin/env bash
# Tokenizes every Velocity JSON template under plugins/ and reports files where
# text was left unscoped or a begin/end rule never closed. Either the grammar
# needs a fix or (more often) the template itself is broken.
# Usage: tools/vscode-velocity-json/test/run.sh [file ...]
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
CODE="${VSCODE_BIN:-/Applications/Visual Studio Code.app/Contents/MacOS/Code}"
GRAMMAR="$HERE/../syntaxes/velocity-json.tmLanguage.json"

if [ $# -gt 0 ]; then files=("$@"); else
	files=("$HERE/edge-cases.json")
	while IFS= read -r -d '' f; do files+=("$f"); done < <(cd "$ROOT" && find "$ROOT/plugins" -name '*.json' -print0 | xargs -0 grep --null -lE '#(if|foreach|jesc|set|end)\b|\$!?\{?[a-zA-Z]')
fi

flagged=0
for f in "${files[@]}"; do
	out=$(ELECTRON_RUN_AS_NODE=1 "$CODE" "$HERE/tokenize.js" "$GRAMMAR" "$f" --errors 2>&1)
	case "$out" in
		*"unscoped tokens: 0, open rules at EOF: 0") ;;
		*) flagged=$((flagged+1)); echo "== ${f#$ROOT/}"; echo "$out" | head -8 ;;
	esac
done
echo "checked ${#files[@]} files, flagged $flagged"
