set -e

source "$LOCAL/bin/utils"

info 'Preparing...'
apt update && apt upgrade -y

install() {
  if ! command_exists java; then
    info 'Installing JRE (Java Runtime Environment)...'
    apt install -y default-jre-headless unzip curl
  else
    apt install -y unzip curl
  fi

  info 'Downloading Kotlin Language Server...'
  LATEST_VERSION=$(curl -s https://api.github.com/repos/fwcd/kotlin-language-server/releases/latest | grep '"tag_name":' | sed -E 's/.*"([^"]+)".*/\1/')
  if [ -z "$LATEST_VERSION" ]; then
    LATEST_VERSION="1.3.1"
  fi
  
  info "Downloading version $LATEST_VERSION..."
  rm -rf /tmp/kotlin-language-server.zip /tmp/kotlin-language-server
  curl -L -o /tmp/kotlin-language-server.zip "https://github.com/fwcd/kotlin-language-server/releases/download/$LATEST_VERSION/server.zip"
  
  info 'Extracting...'
  unzip -q /tmp/kotlin-language-server.zip -d /tmp/kotlin-language-server
  
  info 'Installing...'
  rm -rf /usr/lib/kotlin-language-server
  mkdir -p /usr/lib/kotlin-language-server
  cp -r /tmp/kotlin-language-server/server/* /usr/lib/kotlin-language-server/
  
  ln -sf /usr/lib/kotlin-language-server/bin/kotlin-language-server /usr/bin/kotlin-language-server
  chmod +x /usr/lib/kotlin-language-server/bin/kotlin-language-server
  
  rm -rf /tmp/kotlin-language-server.zip /tmp/kotlin-language-server
  
  info 'Kotlin language server installed successfully.'
  exit 0
}

uninstall() {
  info 'Uninstalling Kotlin language server...'
  rm -rf /usr/lib/kotlin-language-server /usr/bin/kotlin-language-server
  info 'Kotlin language server uninstalled successfully.'
  exit 0
}

update() {
  info 'Updating Kotlin language server...'
  install
}

case "$1" in
  --uninstall) uninstall;;
  --update) update;;
  *) install;;
esac
