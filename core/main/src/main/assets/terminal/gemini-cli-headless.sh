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

if [ -n "${GEMINI_CLI_IDE_SERVER_PORT:-}" ] && [ -n "${GEMINI_CLI_IDE_WORKSPACE_PATH:-}" ]; then
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
fi

if ! command -v gemini >/dev/null 2>&1; then
  log "Installing Gemini CLI..."
  npm install -g --prefix /usr @google/gemini-cli >/dev/null 2>&1
fi

exec gemini "$@"
