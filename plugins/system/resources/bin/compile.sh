#!/usr/bin/env bash

mkdir -p build && \
find plugins/system/code plugins/openedit/code \
	-type f \( -name '*.xml' -o -name '*.properties' \) \
	-exec cp --parents {} build \;
echo "Compiling Java code..." 
javac -g -d build \
	--source 21 --target 21 -nowarn -Xlint:-deprecation \
	-classpath "$(find plugins/system/lib plugins/finder/lib plugins/community/lib \
		-type f \( -name '*.jar' -o -path '*/compile/*.jar' \) | tr '\n' ':')" \
	$(find plugins/openedit/code plugins/system/code plugins/finder/code plugins/community/code \
		-type f -name '*.java')
echo "Compiling Java finished." 
