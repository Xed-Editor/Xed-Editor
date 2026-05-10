#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

cd "${WKDIR:-$HOME}" 2>/dev/null || cd "$HOME"

export NO_UPDATE_NOTIFIER=1
configure_gemini_auth_browser

ensure_node() {
  if ! command_exists node || ! command_exists npm; then
    warn "Node.js/npm is required for Gemini CLI. Installing Node.js LTS..."
    install_nodejs
  fi
}

ensure_gemini_cli() {
  if ! command_exists gemini; then
    info "Installing Gemini CLI..."
    npm install -g --prefix /usr @google/gemini-cli
    info "Gemini CLI installed successfully."
  fi
}

ensure_node
ensure_gemini_cli

info "Starting Gemini CLI in $(pwd)"
exec gemini "$@"
