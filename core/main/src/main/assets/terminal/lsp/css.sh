set -e

info() {
  printf '\033[34;1m[*] \033[0m%s\n' "$1"
}

warn() {
  printf '\033[33;1m[!] \033[0m%s\n' "$1"
}

error() {
  printf '\033[31;1m[x] \033[0m%s\n' "$1"
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}

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
info 'CSS language server installed successfully. Please reopen all tabs or restart the app.'
