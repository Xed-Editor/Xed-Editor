#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# Support both Gemini-specific and generic IDE bridge env vars
IDE_PORT="${GEMINI_CLI_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-}}"
IDE_TOKEN="${GEMINI_CLI_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-}}"
IDE_WS="${GEMINI_CLI_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-}}"

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

export GEMINI_TELEMETRY_ENABLED=false
export GEMINI_TELEMETRY_TARGET=local
export NO_UPDATE_NOTIFIER=1
export PATH="$LOCAL/bin:$PATH"
export EDITOR=vim
export VISUAL=vim
configure_gemini_auth_browser

log() { printf '%s\n' "$*" >&2; }

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required. Installing Node.js LTS..."
  install_nodejs >/dev/null 2>&1
fi

# Configure the Xed Editor IDE bridge as an MCP server
configure_xed_mcp gemini "$IDE_PORT" "$IDE_TOKEN"

if ! command -v gemini >/dev/null 2>&1; then
  log "Installing Gemini CLI..."
  npm install -g --prefix /usr @google/gemini-cli >/dev/null 2>&1
fi

exec gemini "$@"
