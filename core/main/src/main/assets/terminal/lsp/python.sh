set -e

source "$LOCAL/bin/utils"

info 'Preparing installation...'
apt update && apt upgrade -y

info 'Installing pipx...'
apt install -y pipx
pipx ensurepath

info 'Installing python language server...'
pipx install 'python-lsp-server[all]'

info 'Python language server installed successfully. Please reopen all tabs or restart the app.'
