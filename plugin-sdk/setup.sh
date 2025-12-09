#!/bin/bash

DEST="libs"
mkdir -p "$DEST"

# Find all full.jar files inside build/intermediates/full_jar
find .. -name classes.jar \
  -print0 | while IFS= read -r -d '' jar; do
    # extract module name (folder before /build/)
    module=$(echo "$jar" | sed 's/.*\/\([^/]*\)\/build\/.*/\1/')
    outfile="$DEST/${module}.jar"
    cp "$jar" "$outfile"
done

