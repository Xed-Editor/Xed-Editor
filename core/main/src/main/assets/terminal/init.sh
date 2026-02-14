# shellcheck disable=SC2034
force_color_prompt=yes
shopt -s checkwinsize

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:$LOCAL/bin:$PATH
export SHELL="bash"
export PS1="\[\e[1;32m\]\u@\h\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\] \\$ "

source "$LOCAL/bin/utils"

if [ ! -f /etc/apt/apt.conf.d/99translations ]; then
    echo 'Acquire::Languages "none";' > /etc/apt/apt.conf.d/99translations
fi


if dpkg -l | grep -q "^ii  coreutils-from-uutils"; then
    info "uutils coreutils detected."

    apt update -q

    info "Removing coreutils-from-uutils..."

    apt-get --allow-remove-essential remove coreutils-from-uutils rust-coreutils -y || {
        error "Failed to remove uutils."
        exit 1
    }

    info "Installing GNU coreutils..."

    apt-get -y install coreutils-from-gnu || {
        error "Failed to install GNU coreutils."
        exit 1
    }

fi

# Set timezone
CONTAINER_TIMEZONE="UTC"

# Symlink /etc/localtime to the desired timezone
ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime

# Write the timezone string to /etc/timezone
echo "$CONTAINER_TIMEZONE" > /etc/timezone

# Reconfigure tzdata to apply without prompts
DEBIAN_FRONTEND=noninteractive dpkg-reconfigure -f noninteractive tzdata >/dev/null 2>&1


if [[ -f ~/.bashrc ]]; then
    # shellcheck disable=SC1090
    source ~/.bashrc
fi


ensure_packages_once() {
    local marker_file="/.cache/.packages_ensured"
    local PACKAGES=("command-not-found" "sudo" "xkb-data")

    # Exit early if already done
    [[ -f "$marker_file" ]] && return 0

    echo 'APT::Install-Recommends "false";' > /etc/apt/apt.conf.d/99norecommends
    echo 'APT::Install-Suggests "false";' >> /etc/apt/apt.conf.d/99norecommends

    # Create cache dir
    mkdir -p "/.cache"

    # Check for missing packages
    local MISSING=()
    for pkg in "${PACKAGES[@]}"; do
        if ! dpkg -s "$pkg" >/dev/null 2>&1; then
            MISSING+=("$pkg")
        fi
    done

    # If nothing missing, just mark as done
    if [ ${#MISSING[@]} -eq 0 ]; then
        touch "$marker_file"
        return 0
    fi

    info "Installing missing packages: ${MISSING[*]}"

    if export DEBIAN_FRONTEND=noninteractive && \
       apt update -y && \
       apt install -y "${MISSING[@]}"; then
       touch "$marker_file"
       clear
       info "Setup complete."
    else
        error "Failed to install packages."
        return 1
    fi

    # Update command-not-found database
    update-command-not-found 2>/dev/null || true
}


ensure_packages_once
unset -f ensure_packages_once

if [ -x /usr/lib/command-not-found -o -x /usr/share/command-not-found/command-not-found ]; then
	function command_not_found_handle {
	        # check because c-n-f could've been removed in the meantime
                if [ -x /usr/lib/command-not-found ]; then
		   /usr/lib/command-not-found -- "$1"
                   return $?
                elif [ -x /usr/share/command-not-found/command-not-found ]; then
		   /usr/share/command-not-found/command-not-found -- "$1"
                   return $?
		else
		   printf "%s: command not found\n" "$1" >&2
		   return 127
		fi
	}
fi


alias ls='ls --color=auto'
alias grep='grep --color=auto'
alias egrep='egrep --color=auto'
alias fgrep='fgrep --color=auto'
alias pkg='apt'

if [[ -f /initrc ]]; then
    # shellcheck disable=SC1090
    source /initrc
fi

# shellcheck disable=SC2164
cd "$WKDIR" || cd $HOME
