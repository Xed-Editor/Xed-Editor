# inside alpine linux

#todo do to not use chmod
chmod +x /karbon/rootfs/python.sh
chmod +x /karbon/rootfs/nodejs.sh
chmod +x /karbon/rootfs/java.sh

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

# setup package or start processes before starting the shell

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
    apk update && apk upgrade
    apk add $missing_packages
    if [ $? -eq 0 ]; then
        echo -e "\e[32mSuccessfully Installed\e[0m"
    fi
    echo -e "\e[32mUse apk to install new packages\e[0m"
fi

if [ "$#" -eq 0 ]; then
    $START_SHELL
else
    # shellcheck disable=SC2068
    $@
fi
