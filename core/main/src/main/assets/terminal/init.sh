info() {
  printf '\033[34;1m[*] \033[0m%s\n' "$1"
}

warn() {
  printf '\033[33;1m[!] \033[0m%s\n' "$1"
}

error() {
  printf '\033[31;1m[x] \033[0m%s\n' "$1"
}

confirm() {
  # $1 = prompt message
  printf '%s [y/N]: ' "$1"
  read -r reply
  case "$reply" in
    [yY]|[yY][eE][sS]) return 0 ;;
    *) return 1 ;;
  esac
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
      mv "$ALPINE_DIR/root" "/home/alpine-data/" 2>/dev/null
      mv "$ALPINE_DIR/home" "/home/alpine-data/" 2>/dev/null
      info "Data migration completed."
    else
      warn "Skipped data migration."
    fi

    if confirm "Do you want to delete the Alpine installation to free up space?"; then
      info "Deleting Alpine installation..."
      rm -rf "$ALPINE_DIR"
      info "Alpine has been removed."
    else
      warn "Alpine installation retained."
      touch "$RETAINED_FILE"
    fi
  fi
fi

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/games:/usr/local/bin:/usr/local/sbin

# Continue with the rest of the script
cd "$WKDIR" || { error "Failed to change directory to $WKDIR"; exit 1; }
exec bash