#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

cd "${WKDIR:-$HOME}" 2>/dev/null || cd "$HOME"

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

if [ -z "${GEMINI_API_KEY:-}" ] && [ -z "${GOOGLE_API_KEY:-}" ]; then
  warn "No GEMINI_API_KEY/GOOGLE_API_KEY is set. If Gemini asks for auth, follow its prompt or export your API key in ~/.bashrc."
fi

info "Starting Gemini CLI in $(pwd)"
exec gemini "$@"
