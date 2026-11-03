#!/usr/bin/env bash
# Regression test: checks test/fixtures and compares with test/expected.txt.
#   test/run.sh            compare
#   test/run.sh --update   rewrite expected.txt from the current output
set -uo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../.." && pwd)"
cd "$ROOT"
actual="$("$HERE/../check.sh" "$HERE"/fixtures/*.json "$HERE"/fixtures/partials/*.json 2>&1 | sed "s#tools/velocity-json-check/test/fixtures/##")"
if [ "${1:-}" = "--update" ]; then
	printf '%s\n' "$actual" > "$HERE/expected.txt"
	echo "updated $HERE/expected.txt"
	exit 0
fi
if diff -u "$HERE/expected.txt" <(printf '%s\n' "$actual"); then
	echo "PASS"
else
	echo "FAIL (run test/run.sh --update if the change is intended)"
	exit 1
fi
