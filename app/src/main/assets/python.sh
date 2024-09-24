required_packages="python3 py3-pip"

missing_packages=""

for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

if [ -n "$missing_packages" ]; then
    apk add $missing_packages
fi

python3 $@