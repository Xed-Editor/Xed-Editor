set -e

source "$LOCAL/bin/utils"

info 'Preparing...'
apt update && apt upgrade -y

install() {
  if ! command_exists node || ! command_exists npm; then
    install_nodejs
  fi

  info 'Installing Bash language server...'
  npm install -g --prefix /usr bash-language-server

  info 'Installing ShellCheck...'
  apt install -y shellcheck

  info 'Bash language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Bash language server...'
  npm uninstall -g --prefix /usr bash-language-server
  info 'Bash language server uninstalled successfully.'
  uninstall_nodejs
  exit 0
}

update() {
  info 'Updating Bash language server...'
  npm update -g --prefix /usr bash-language-server
  info 'Bash language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac

