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

info 'Installing bash language server...'
npm i -g bash-language-server

info 'Installing ShellCheck...'
pkg install -y shellcheck

clear
info 'Bash language server installed successfully. Please reopen all tabs or restart the app.'
