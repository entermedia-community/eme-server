#!/usr/bin/env bash
#
# Compiles every plugins/*/code folder, using every jar in plugins/*/lib.
#
# Requires Java 21+. If you don't have one, install it with sdkman (works on Linux and macOS):
#   curl -s "https://get.sdkman.io" | bash
#   source "$HOME/.sdkman/bin/sdkman-init.sh"
#   sdk install java 21-tem      # or: sdk list java   to pick another 21+ build
#   sdk use java 21-tem          # current shell only; `sdk default java 21-tem` makes it permanent

BUILD_DIR="${BUILD_DIR:-build}"

mkdir -p "$BUILD_DIR"

# Portable (bash 3.2 / BSD find) discovery of plugin folders
CODE_DIRS=()
LIB_DIRS=()
for d in plugins/*/code; do [ -d "$d" ] && CODE_DIRS+=("$d"); done
for d in plugins/*/lib;  do [ -d "$d" ] && LIB_DIRS+=("$d");  done

if [ ${#CODE_DIRS[@]} -eq 0 ]; then
	echo "No plugins/*/code folders found (run from the server root)." >&2
	exit 1
fi

# Copy non-java resources
find "${CODE_DIRS[@]}" \
	-type f \( -name '*.xml' -o -name '*.properties' \) \
	-exec cp {} "$BUILD_DIR" \;

CLASSPATH_LIST=""
if [ ${#LIB_DIRS[@]} -gt 0 ]; then
	CLASSPATH_LIST="$(find "${LIB_DIRS[@]}" -type f -name '*.jar' | tr '\n' ':')"
fi

# Use an argfile for the sources to avoid command-line length limits
SOURCES_FILE="$(mktemp "${TMPDIR:-/tmp}/eme-sources.XXXXXX")"
trap 'rm -f "$SOURCES_FILE"' EXIT
find "${CODE_DIRS[@]}" -type f -name '*.java' > "$SOURCES_FILE"

echo "Compiling Java code..."
javac -g -d "$BUILD_DIR" \
	--source 21 --target 21 -nowarn -Xlint:-deprecation -Xlint:-removal \
	-classpath "$CLASSPATH_LIST" \
	@"$SOURCES_FILE"
STATUS=$?
echo "Compiling Java finished."
exit $STATUS
