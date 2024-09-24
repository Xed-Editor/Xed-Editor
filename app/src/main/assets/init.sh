# inside alpine linux

chmod +x /karbon/rootfs/python.sh
export PIP_BREAK_SYSTEM_PACKAGES=1

unset LD_LIBRARY_PATH
unset PROOT_TMP_DIR

PREFIX_PATH=/data/data/com.rk.xededitor
FILE_PATH="$PREFIX_PATH/shell"
if [ -s "$FILE_PATH" ]; then
    START_SHELL=$(cat "$FILE_PATH")
else
    START_SHELL="/bin/sh"
fi

# setup package or start processes from before starting the shell

# List of necessary packages
required_packages="gcompat glib git bash nano sudo"

# Check if each package is installed
missing_packages=""

for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

# Install
if [ -n "$missing_packages" ]; then
    echo -e "\e[32mInstalling Important packages\e[0m"
    apk add $missing_packages
    if [ $? -eq 0 ]; then
        echo -e "\e[32mSuccessfully Installed\e[0m"
    fi
fi

if [ "$#" -eq 0 ]; then
    $START_SHELL
else
    # shellcheck disable=SC2068
    $@
fi
