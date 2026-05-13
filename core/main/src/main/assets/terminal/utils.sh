RESET='\033[0m'

BOLD_BLUE='\033[1;34m'
BOLD_YELLOW='\033[1;33m'
BOLD_RED='\033[1;31m'

BLUE_BG='\033[1;44m'
YELLOW_BG='\033[1;43m'
RED_BG='\033[1;41m'

info() {
  printf "\n${BLUE_BG}  INFO  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$1"
}

warn() {
  printf "\n${YELLOW_BG}  WARN  ${RESET} ${BOLD_YELLOW}%s${RESET}\n" "$1"
}

error() {
  printf "\n${RED_BG} ERROR ${RESET} ${BOLD_RED}%s${RESET}\n" "$1"
}

ask() {
  local prompt="$1"
  local response

  while true; do
    printf "\n${BLUE_BG}  ?  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$prompt"
    read -rp "[y/N]: " response
    case "$response" in
      [Yy]|[Yy][Ee][Ss])
        return 0
        ;;
      [Nn]|[Nn][Oo]|"")
        return 1
        ;;
      *)
        warn "Please answer yes or no."
        ;;
    esac
  done
}

install_nodejs() {
  info "Installing Node.js LTS..."
  apt install -y curl ca-certificates
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs
}

uninstall_nodejs() {
  if ask "Do you want to uninstall Node.js LTS? It was installed as a dependency of this language server. This will also remove all globally installed npm packages."; then
    info "Uninstalling Node.js LTS..."
    apt remove -y nodejs
    apt autoremove -y
    info "Node.js LTS uninstalled successfully."
  fi
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}