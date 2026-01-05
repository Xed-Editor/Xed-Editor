set -e

source "$LOCAL/bin/utils"

info 'Preparing installation...'
apt update && apt upgrade -y

install_nodejs() {
  info "Installing Node.js LTS..."
  apt install -y curl ca-certificates
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs

  # Remove old installation
  if [ -d "$HOME/.npm-global" ]; then
    npm uninstall -g --prefix $HOME/.npm-global vscode-langservers-extracted
  fi
}

if ! command_exists node || ! command_exists npm; then
  install_nodejs
fi

info 'Installing extracted VSCode language extensionServers...'
npm install -g --prefix /usr vscode-langservers-extracted

info 'Markdown language server installed successfully. Please reopen all tabs or restart the app.'
