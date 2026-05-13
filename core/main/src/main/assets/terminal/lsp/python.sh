set -e

source "$LOCAL/bin/utils"

info 'Preparing...'
apt update && apt upgrade -y

install() {
  info 'Installing pipx...'
  apt install -y pipx
  pipx ensurepath

  info 'Installing Python language server...'
  pipx install 'python-lsp-server[all]'

  info 'Python language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Python language server...'
  pipx uninstall python-lsp-server
  info 'Python language server uninstalled successfully.'

  if ask "Do you want to uninstall pipx? It was installed as a dependency of this language server."; then
    info "Uninstalling pipx..."
    apt remove -y pipx
    apt autoremove -y
    info "Pipx uninstalled successfully."
  fi
  exit 0
}

update() {
  info 'Updating Python language server...'
  pipx upgrade python-lsp-server
  info 'Python language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac

