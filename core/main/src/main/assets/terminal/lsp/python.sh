set -e

source "$LOCAL/bin/utils"

info 'Preparing installation...'
apt update && apt upgrade -y

info 'Installing pipx...'
apt install -y pipx
pipx ensurepath

info 'Installing Python language server...'
pipx install 'python-lsp-server[all]'

info 'Python language server installed successfully.'
