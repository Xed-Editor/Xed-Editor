# shellcheck disable=SC2034
force_color_prompt=yes
shopt -s checkwinsize

info() {
  printf '\033[34;1m[*] \033[0m%s\n' "$1"
}

warn() {
  printf '\033[33;1m[!] \033[0m%s\n' "$1"
}

error() {
  printf '\033[31;1m[x] \033[0m%s\n' "$1"
}

ALPINE_DIR="$PREFIX/local/alpine"
RETAINED_FILE="$ALPINE_DIR/.retained"

if [ -d "$ALPINE_DIR" ]; then
  if [ -f "$RETAINED_FILE" ]; then
    :
  else
    info "Detected existing Alpine installation"
    printf "\nxed-editor has now migrated from Alpine to Ubuntu for better compatibility and support.\n\n"

    if confirm "Do you want to migrate your home data from Alpine to Ubuntu?"; then
      info "Migrating data..."
      mkdir -p "/home/alpine-data"
      cp -r "$ALPINE_DIR/root" "/home/alpine-data/" 2>/dev/null
      cp -r "$ALPINE_DIR/home" "/home/alpine-data/" 2>/dev/null

      info "Data migration completed."
    else
      warn "Skipped data migration."
    fi

    if confirm "Do you want to delete the Alpine installation to free up space?"; then
      info "Deleting Alpine installation..."
      xed exec rm -rf "$ALPINE_DIR"
      info "Alpine has been removed."
    else
      warn "Alpine installation retained."
      touch "$RETAINED_FILE"
    fi
  fi
fi


if [[ -f ~/.bashrc ]]; then
    # shellcheck disable=SC1090
    source ~/.bashrc
fi

bridge="$NATIVE_LIB_DIR/libbridge.so"
[ -f "$bridge" ] && rm -f "$PREFIX/local/bin/xed" && ln -s "$bridge" "$PREFIX/local/bin/xed"

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin:$PREFIX/local/bin:$PATH
export SHELL="bash"
export PS1="\[\e[1;32m\]\u@\h\[\e[0m\]:\[\e[1;34m\]\w\[\e[0m\] # "

# ensure command-not-found is installed
if ! dpkg -s command-not-found >/dev/null 2>&1; then
     info "Updating... please wait."
     DEBIAN_FRONTEND=noninteractive
     apt update -y && apt install -y command-not-found sudo
     # initialize database (important for first time use)
     apt update
fi

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
alias editor="xed edit"
alias edit="xed edit"


cd "$WKDIR" || { error "Failed to change directory to $WKDIR"; exit 1; }


if [[ -f /initrc ]]; then
    # shellcheck disable=SC1090
    source /initrc
fi


