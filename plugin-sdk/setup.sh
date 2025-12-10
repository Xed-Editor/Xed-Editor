#!/bin/bash

DEST="libs"
mkdir -p "$DEST"

find .. -name classes.jar \
  -print0 | while IFS= read -r -d '' jar; do
    module=$(echo "$jar" | sed 's/.*\/\([^/]*\)\/build\/.*/\1/')
    outfile="$DEST/${module}.jar"
    cp "$jar" "$outfile"
done

find .. -type f -name .ignore_plugin_sdk -print0 | while IFS= read -r -d '' file; do
  file_to_remove="$DEST/$(basename "$(dirname "$file")").jar"
  if [ -f "$file_to_remove" ]; then
      rm "$file_to_remove"
  fi
done

