info() {
  printf '\n\033[1;44m  INFO  \033[0m \033[1;34m%s\033[0m\n' "$1"
}

warn() {
  printf '\n\033[1;43m  WARN  \033[0m \033[1;33m%s\033[0m\n' "$1"
}

error() {
  printf '\n\033[1;41m ERROR \033[0m \033[1;31m%s\033[0m\n' "$1"
}


command_exists() {
  command -v "$1" >/dev/null 2>&1
}