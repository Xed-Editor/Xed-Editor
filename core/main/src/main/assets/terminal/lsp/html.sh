set -e

source "$(dirname "$0")/../utils"

info 'Preparing installation...'
apt update && apt upgrade -y

install_nodejs() {
  info "Installing Node.js LTS..."
  apt install -y curl
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs
  mkdir -p /home/.npm-global
  npm config set prefix '/home/.npm-global'
  grep -qxF "export PATH=\"/home/.npm-global/bin:\$PATH\"" ~/.bashrc || \
      echo "export PATH=\"/home/.npm-global/bin:\$PATH\"" >> ~/.bashrc
  export PATH="/home/.npm-global/bin:$PATH"
}

if ! command_exists node || ! command_exists npm; then
  install_nodejs
fi

info 'Installing extracted VSCode language servers...'
npm install -g vscode-langservers-extracted

clear
info 'HTML language server installed successfully. Please reopen all tabs or restart the app.'
