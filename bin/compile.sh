#!/usr/bin/env bash

BUILD_DIR="${BUILD_DIR:-build}"

mkdir -p "$BUILD_DIR"

find plugins/system/code \
	-type f \( -name '*.xml' -o -name '*.properties' \) \
	-exec cp {} "$BUILD_DIR" \;

find plugins/openedit/code \
	-type f \( -name '*.xml' -o -name '*.properties' \) \
	-exec cp {} "$BUILD_DIR" \;

echo "Compiling Java code..." 
javac -g -d "$BUILD_DIR" \
	--source 21 --target 21 -nowarn -Xlint:-deprecation -Xlint:-removal \
	-classpath "$(find plugins/system/lib plugins/finder/lib plugins/community/lib \
		-type f \( -name '*.jar' -o -path '*/compile/*.jar' \) | tr '\n' ':')" \
	$(find plugins/openedit/code plugins/system/code plugins/finder/code plugins/community/code \
		-type f -name '*.java')
echo "Compiling Java finished." 



