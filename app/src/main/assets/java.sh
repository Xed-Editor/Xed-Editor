if [ ! apk info -e openjdk17 >/dev/null 2>&1 ]; then
    echo "Installing OpenJDK-17..."
    apk add openjdk17
fi

echo "Compiling with $1..."

if [ "$1" = "javac" ]; then
    package_path=$(echo "$2" | sed 's:.*/src/::' | sed 's:/:\.:g' | sed 's:\.java$::')

    cd $(echo "$2" | sed 's:/src/.*:/src:')

    classpath=""
    if [ -d "../libs" ]; then
        classpath=$(find ../libs -name "*.jar" -exec realpath {} \; | tr '\n' ':')
    fi
    if [ -d "../lib" ]; then
        lib_classpath=$(find ../lib -name "*.jar" -exec realpath {} \; | tr '\n' ':')
        classpath="$classpath$lib_classpath"
    fi
    classpath=${classpath%:}

    if [ -n "$classpath" ]; then
        javac -cp "$classpath" $(find . -name "*.java") -d out
    else
        javac $(echo "$package_path" | sed 's:\.:/:g').java -d out
    fi
    if [ $? -ne 0 ]; then
        exit 1
    fi

    clear
    cd out
    [ -d ../../resources ] && cp -r ../../resources .
    java  -XX:+UseParallelGC -cp ".:$classpath" $(echo "$package_path" | sed 's:\.:/:g')
    [ -d ../../resources ] && rm -r resources
fi