#!/bin/bash

# Change the current working directory to the directory where this script resides.
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Now, all subsequent commands will run from the directory of this script.

rm -rf .gradle
rm -rf output/
rm -rf libs/

bash setup.sh

./gradlew shadowJar