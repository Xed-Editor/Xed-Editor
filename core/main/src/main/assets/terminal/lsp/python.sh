set -e

source "$LOCAL/bin/utils"

info 'Preparing installation...'
pkg update -y && pkg upgrade -y

info 'Installing python language server...'
pip install 'python-lsp-server[all]'

clear
info 'Python language server installed successfully. Please reopen all tabs or restart the app.'
