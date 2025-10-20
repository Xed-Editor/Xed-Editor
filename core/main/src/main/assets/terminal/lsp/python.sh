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

info 'Installing pipx...'
apt install -y pipx
pipx ensurepath

info 'Installing python language server...'
pipx install 'python-lsp-server[all]'

clear
info 'Python language server installed successfully. Please reopen all tabs or restart the app.'
