set -e

source "$LOCAL/bin/utils"

info 'Preparing...'
apt update && apt upgrade -y

install() {
  info 'Installing LemMinX language server...'
  mkdir -p $HOME/.lsp/lemminx
  cd $HOME/.lsp/lemminx
  apt install -y curl ca-certificates
  curl -L -o server.jar https://download.eclipse.org/staging/2025-09/plugins/org.eclipse.lemminx.uber-jar_0.31.0.jar
  echo "0.31.0" > version.txt
  apt install -y default-jdk
  info 'LemMinX language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling LemMinX language server...'
  rm -rf $HOME/.lsp/lemminx
  info 'LemMinX language server uninstalled successfully.'

  if ask "Do you want to uninstall OpenJDK? It was installed as a dependency of this language server."; then
    info "Uninstalling OpenJDK..."
    apt remove -y default-jdk
    apt autoremove -y
    info "OpenJDK uninstalled successfully."
  fi
  exit 0
}

update() {
  info 'Updating LemMinX language server...'
  cd $HOME/.lsp/lemminx
  rm server.jar
  curl -L -o server.jar https://download.eclipse.org/staging/2025-09/plugins/org.eclipse.lemminx.uber-jar_0.31.0.jar
  echo "0.31.0" > version.txt
  info 'LemMinX language server updated successfully.'
  exit 0
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
