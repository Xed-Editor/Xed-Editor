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

configure_xed_ide_integration() {
  [ -n "${GEMINI_CLI_IDE_SERVER_PORT:-}" ] || return 0
  [ -n "${GEMINI_CLI_IDE_WORKSPACE_PATH:-}" ] || return 0
  mkdir -p "$HOME/.gemini"
  node <<'NODE'
const fs = require('fs');
const os = require('os');
const path = require('path');
const settingsFile = path.join(os.homedir(), '.gemini', 'settings.json');
let settings = {};
try {
  settings = JSON.parse(fs.readFileSync(settingsFile, 'utf8'));
} catch (_) {}
settings.ide = { ...(settings.ide || {}), enabled: true, hasSeenNudge: true };
fs.writeFileSync(settingsFile, JSON.stringify(settings, null, 2));
NODE
}

ensure_node
configure_xed_ide_integration
ensure_gemini_cli

info "Starting Gemini CLI in $(pwd)"
exec gemini "$@"
