#!/bin/bash

PROJECT_ROOT=/home/rohit/AndroidStudioProjects/Xed-Editor
CORE_DIR="$PROJECT_ROOT/core"
OUTPUT_DIR="./libs"
mkdir -p "$OUTPUT_DIR"
MODULES=$(find "$CORE_DIR" -mindepth 1 -maxdepth 1 -type d)
for MODULE in $MODULES; do
    FULL_JAR_PATH="$MODULE/build/intermediates/full_jar/release/createFullJarRelease/full.jar"
    if [[ -f "$FULL_JAR_PATH" ]]; then
        # Extract the module name to name the output file
        MODULE_NAME=$(basename "$MODULE")
        OUTPUT_JAR="$OUTPUT_DIR/$MODULE_NAME.jar"
        cp "$FULL_JAR_PATH" "$OUTPUT_JAR"
    fi
done
