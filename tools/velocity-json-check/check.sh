#!/usr/bin/env bash
# Render-and-parse check for Velocity-templated .json files.
#
#   tools/velocity-json-check/check.sh                  # every .json template under plugins/
#   tools/velocity-json-check/check.sh path/to/a.json   # specific files
#   tools/velocity-json-check/check.sh --json a.json    # machine-readable, one JSON line per file
#   tools/velocity-json-check/check.sh --server         # long-running: one path per stdin line (used by the VS Code extension)
#
# Uses the server's own jars (plugins/*/lib) and compiled classes (build/, from bin/compile.sh).
# Exit code 1 when any error is found.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OUT="$HERE/build"
SRC="$HERE/src/VelocityJsonCheck.java"

if [ -n "${JAVA_HOME:-}" ]; then JAVA="$JAVA_HOME/bin/java"; JAVAC="$JAVA_HOME/bin/javac"; else JAVA=java; JAVAC=javac; fi

if [ ! -f "$ROOT/build/org/entermediadb/asset/util/JsonUtil.class" ]; then
	echo "Server classes not compiled. Run bin/compile.sh from $ROOT first." >&2
	exit 2
fi

CP="$ROOT/build"
for jar in "$ROOT"/plugins/*/lib/*.jar; do CP="$CP:$jar"; done

if [ ! -f "$OUT/VelocityJsonCheck.class" ] || [ "$SRC" -nt "$OUT/VelocityJsonCheck.class" ]; then
	mkdir -p "$OUT"
	"$JAVAC" -nowarn -Xlint:none -d "$OUT" -cp "$CP" "$SRC" >&2
fi

ARGS=()
has_files=0
for a in "$@"; do
	case "$a" in
		--*) ARGS+=("$a") ;;
		/*) ARGS+=("$a"); has_files=1 ;;
		*) ARGS+=("$PWD/$a"); has_files=1 ;;
	esac
done
if [ $has_files -eq 0 ] && [[ " $* " != *" --server "* ]]; then
	while IFS= read -r -d '' f; do ARGS+=("$ROOT/$f"); done < <(cd "$ROOT" && find plugins -name '*.json' -print0 | xargs -0 grep --null -lE '#(if|foreach|jesc|set|end)\b|\$!?\{?[a-zA-Z]')
fi

cd "$ROOT"
exec "$JAVA" -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xshare:auto -cp "$OUT:$CP" VelocityJsonCheck --root "$ROOT" ${ARGS[@]+"${ARGS[@]}"}
