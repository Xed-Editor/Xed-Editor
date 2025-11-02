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

info 'Installing extracted VSCode language servers...'
npm install -g vscode-langservers-extracted

clear
info 'Markdown language server installed successfully. Please reopen all tabs or restart the app.'
