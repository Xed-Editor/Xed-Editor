#!/bin/bash

# Required packages for Alpine Linux
required_packages="bash gcc build-base make cmake"
missing_packages=""

# Check for missing packages
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

# Install missing packages
if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*]\e[37m Installing missing packages: $missing_packages\e[0m"
    apk add $missing_packages
fi

TARGET_FILE=$1

# Function to run the compiled code
run_code(){
    echo -e "\e[32;1m[✓]\e[37m Compilation successful! Running...\e[0m"
    chmod +x "$OUTPUT_FILE"
        if [ -x "$TARGET_FILE" ]; then
            "$OUTPUT_FILE"
        else
            mv "$OUTPUT_FILE" /tmp/a.out
            chmod +x /tmp/a.out
            /tmp/a.out
        fi
}

# Check if the target is a file
if [ -f "$TARGET_FILE" ]; then
    if echo "$TARGET_FILE" | grep -qE '\.c$'; then
        COMPILER="gcc"
    elif echo "$TARGET_FILE" | grep -qE '\.(cpp|cc)$'; then
        COMPILER="g++"
    else
        echo "Unsupported file type. Provide a .c or .cpp file."
        exit 1
    fi

    OUTPUT_FILE="${TARGET_FILE%.*}"
    echo -e "\e[34;1m[*]\e[37m Compiling file: $TARGET_FILE\e[0m"
    if $COMPILER -o "$OUTPUT_FILE" "$TARGET_FILE"; then
        run_code
    else
        echo -e "\e[31;1m[✗]\e[37m Compilation failed!\e[0m"
        exit 1
    fi

# Check if the target is a directory (Make or CMake project)
elif [ -d "$TARGET_FILE" ]; then
    if [ -f "$TARGET_FILE/Makefile" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with Makefile\e[0m"
        if make -C "$TARGET_FILE"; then
            OUTPUT_FILE=$(find "$TARGET_FILE" -maxdepth 1 -type f -executable | head -n 1)
            if [ -n "$OUTPUT_FILE" ]; then
                run_code
            else
                echo -e "\e[31;1m[✗]\e[37m No executable found after make.\e[0m"
            fi
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    elif [ -f "$TARGET_FILE/CMakeLists.txt" ]; then
        echo -e "\e[34;1m[*]\e[37m Building with CMake\e[0m"
        mkdir -p "$TARGET_FILE/build"
        cd "$TARGET_FILE/build"
        if cmake .. && make; then
            OUTPUT_FILE=$(find . -maxdepth 1 -type f -executable | head -n 1)
            if [ -n "$OUTPUT_FILE" ]; then
                run_code
            else
                echo -e "\e[31;1m[✗]\e[37m No executable found after cmake build.\e[0m"
            fi
        else
            echo -e "\e[31;1m[✗]\e[37m Build failed!\e[0m"
            exit 1
        fi
    else
        echo "No Makefile or CMakeLists.txt found in $TARGET_FILE"
        exit 1
    fi
else
    echo "File or directory not found: $TARGET_FILE"
    exit 1
fi
