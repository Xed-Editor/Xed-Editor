set -e

source "$LOCAL/bin/utils"

info 'Preparing installation...'
pkg update -y && pkg upgrade -y

install_nodejs() {
  pkg i nodejs -y
}

if ! command_exists node || ! command_exists npm; then
  install_nodejs
fi

info 'Installing typescript language server...'
npm install -g typescript typescript-language-server

clear
info 'TypeScript language server installed successfully. Please reopen all tabs or restart the app.'
