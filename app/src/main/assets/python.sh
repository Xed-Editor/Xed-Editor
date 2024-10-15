# List of necessary packages
required_packages="python3 python3-pip"

missing_packages=""

for pkg in $required_packages; do
    # Check if each package is installed
    if ! dpkg -l | grep -q "^ii  $pkg"; then
        missing_packages="$missing_packages $pkg"
    fi
done

# Install missing packages if any
if [ -n "$missing_packages" ]; then
    echo "Installing missing packages: $missing_packages"
    apt update
    apt install -y $missing_packages
fi

# Run the python3 command with arguments
# shellcheck disable=SC2068
python3 "$@"
