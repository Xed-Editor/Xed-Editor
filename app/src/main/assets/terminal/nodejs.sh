# List of necessary packages
required_packages="nodejs npm"

missing_packages=""

for pkg in $required_packages; do
    # Check if each package is installed
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

# Install missing packages if any
if [ -n "$missing_packages" ]; then
    echo "Installing missing packages: $missing_packages"
    apk add $missing_packages
fi

# Run the node command with arguments
# shellcheck disable=SC2068
node $@