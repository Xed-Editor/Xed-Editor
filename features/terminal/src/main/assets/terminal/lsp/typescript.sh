set -e

source "$LOCAL/bin/utils"

install() {
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info "Installing TypeScript (including language server)..."
  npm install -g --prefix /usr typescript@latest
  info 'TypeScript language server installed successfully.'
  exit 0
}

uninstall() {
  info "Uninstalling TypeScript (including language server)..."
  npm uninstall -g --prefix /usr typescript typescript-language-server
  info 'TypeScript language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info "Updating TypeScript (including language server)..."
  npm install -g --prefix /usr typescript@latest
  info 'TypeScript language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
