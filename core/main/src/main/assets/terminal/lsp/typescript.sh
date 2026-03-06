set -e

source "$LOCAL/bin/utils"

info 'Preparing...'
apt update && apt upgrade -y

install() {
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info "Installing TypeScript language server..."
  npm install -g --prefix /usr typescript typescript-language-server
  info 'TypeScript language server installed successfully.'
  exit 0
}

uninstall() {
  info "Uninstalling TypeScript language server..."
  npm uninstall -g --prefix /usr typescript typescript-language-server
  info 'TypeScript language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info "Updating TypeScript language server..."
  npm update -g --prefix /usr typescript typescript-language-server
  info 'TypeScript language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
