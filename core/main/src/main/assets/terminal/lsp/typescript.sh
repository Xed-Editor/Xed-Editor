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
    npm uninstall -g --prefix $HOME/.npm-global typescript
    npm uninstall -g --prefix $HOME/.npm-global typescript-language-server
  fi
}

if ! command_exists node || ! command_exists npm; then
  install_nodejs
fi

info 'Installing TypeScript language server...'
npm install -g --prefix /usr typescript typescript-language-server

info 'TypeScript language server installed successfully.'
