set -e  # Exit immediately on Failure

export PIP_BREAK_SYSTEM_PACKAGES=1
unset LD_LIBRARY_PATH
unset PROOT_TMP_DIR

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export PS1="\[\e[38;5;46m\]\u\[\e[38;5;231m\]@\[\e[38;5;231m\]\h \[\e[38;5;231m\]\w \[\033[0m\]\\$ "

START_SHELL="/bin/sh"

required_packages="bash gcompat glib git nano sudo file"

# Check if each package is installed
missing_packages=""

for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

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