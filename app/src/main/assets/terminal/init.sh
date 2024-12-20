set -e  # Exit immediately on Failure

export PIP_BREAK_SYSTEM_PACKAGES=1
unset LD_LIBRARY_PATH
unset PROOT_TMP_DIR

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export PROMPT_DIRTRIM=2
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@karbon \[\033[39m\]\w \[\033[0m\]\\$ "

START_SHELL="/bin/bash"

required_packages="bash gcompat glib git nano sudo file build-base"

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