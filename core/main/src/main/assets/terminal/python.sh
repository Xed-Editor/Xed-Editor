required_packages="python3 pip"
missing_packages=""
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done
if [ -n "$missing_packages" ]; then
    echo "Installing missing packages: $missing_packages"
    apk add $missing_packages
fi
# shellcheck disable=SC2068
python3 $@