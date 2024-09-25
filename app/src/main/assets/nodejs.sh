required_packages="nodejs npm"

missing_packages=""

for pkg in $required_packages; do
    # shellcheck disable=SC2086
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

if [ -n "$missing_packages" ]; then
    # shellcheck disable=SC2086
    apk add $missing_packages
fi

# shellcheck disable=SC2068
node $@