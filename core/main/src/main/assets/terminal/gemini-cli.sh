#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

# Support both Gemini-specific and generic IDE bridge env vars
IDE_PORT="${GEMINI_CLI_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-}}"
IDE_TOKEN="${GEMINI_CLI_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-}}"
IDE_WS="${GEMINI_CLI_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-}}"

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

export NO_UPDATE_NOTIFIER=1
export GEMINI_TELEMETRY_ENABLED=false
export GEMINI_TELEMETRY_TARGET=local
export PATH="$LOCAL/bin:$PATH"
export EDITOR=vim
export VISUAL=vim
configure_gemini_auth_browser

ensure_node() {
  if ! command_exists node || ! command_exists npm; then
    warn "Node.js/npm is required. Installing Node.js LTS..."
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
  [ -n "$IDE_PORT" ] || return 0
  [ -n "$IDE_WS" ] || return 0
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
settings.general = { ...(settings.general || {}), preferredEditor: 'vim' };
settings.ide = { ...(settings.ide || {}), enabled: true, hasSeenNudge: true };
settings.privacy = { ...(settings.privacy || {}), usageStatisticsEnabled: false };
settings.telemetry = { ...(settings.telemetry || {}), enabled: false };
fs.writeFileSync(settingsFile, JSON.stringify(settings, null, 2));
NODE
}

ensure_node
configure_xed_ide_integration
ensure_gemini_cli

info "Starting Gemini CLI in $(pwd)"
exec gemini "$@"
