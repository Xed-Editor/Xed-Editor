#!/usr/bin/env bash
set -e

# Headless Gemini wrapper for in-editor AI actions.
# stdout should contain Gemini response only; setup/status logs go to stderr.
source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

cd "${WKDIR:-$HOME}" 2>/dev/null || cd "$HOME"

export GEMINI_TELEMETRY_ENABLED=false
export GEMINI_TELEMETRY_TARGET=local
export NO_UPDATE_NOTIFIER=1
configure_gemini_auth_browser

log() { printf '%s\n' "$*" >&2; }

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required. Installing Node.js LTS..."
  install_nodejs >/dev/null 2>&1
fi

if ! command -v gemini >/dev/null 2>&1; then
  log "Installing Gemini CLI..."
  npm install -g --prefix /usr @google/gemini-cli >/dev/null 2>&1
fi

exec gemini "$@"
