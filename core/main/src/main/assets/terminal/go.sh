required_packages="go"
missing_packages=""
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done
if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*]\e[37m Installing missing packages: $missing_packages\e[0m"
    apk add $missing_packages
fi
# shellcheck disable=SC2068
go run $@
