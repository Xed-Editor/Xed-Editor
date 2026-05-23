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
  BRIDGE_OK=$(curl -sf "http://127.0.0.1:${IDE_PORT}/health" 2>/dev/null || echo "")
  if [ -z "$BRIDGE_OK" ]; then
    warn "IDE bridge not reachable on port $IDE_PORT"
  fi
  mkdir -p "$HOME/.gemini"
  SETTINGS_FILE="$HOME/.gemini/settings.json"
  # Merge MCP config into Gemini settings using python3 (preferred) or node
  if command_exists python3; then
    python3 -c "
import json
try:
    with open('$SETTINGS_FILE') as f:
        s = json.load(f)
except:
    s = {}
s.setdefault('general', {})['preferredEditor'] = 'vim'
s.setdefault('ide', {})['enabled'] = True
s.setdefault('ide', {})['hasSeenNudge'] = True
s.setdefault('privacy', {})['usageStatisticsEnabled'] = False
s.setdefault('telemetry', {})['enabled'] = False

# Gemini CLI uses 'mcpServers' for server definitions, not 'mcp'
ms = s.setdefault('mcpServers', {})
ms['xed-ide'] = {
    'url': 'http://127.0.0.1:${IDE_PORT}/mcp',
    'headers': {'Authorization': 'Bearer ${IDE_TOKEN}'}
}

# Cleanup any accidental 'xed-ide' entry in the 'mcp' object (used for global settings)
if 'mcp' in s: s['mcp'].pop('xed-ide', None)

with open('$SETTINGS_FILE', 'w') as f:
    json.dump(s, f, indent=2)
" 2>/dev/null || fallback_to_node=true
  fi
  if [ "${fallback_to_node:-false}" = true ]; then
    warn "Falling back to Node.js for Gemini settings merge"
    export IDE_PORT IDE_TOKEN
    node <<'NODE'
const fs = require('fs');
const os = require('os');
const path = require('path');
const settingsFile = path.join(os.homedir(), '.gemini', 'settings.json');
const idePort = process.env.IDE_PORT || '0';
const ideToken = process.env.IDE_TOKEN || '';
let s = {};
try { s = JSON.parse(fs.readFileSync(settingsFile, 'utf8')); } catch (_) {}
s.general = { ...(s.general || {}), preferredEditor: 'vim' };
s.ide = { ...(s.ide || {}), enabled: true, hasSeenNudge: true };
s.privacy = { ...(s.privacy || {}), usageStatisticsEnabled: false };
s.telemetry = { ...(s.telemetry || {}), enabled: false };

// Gemini CLI uses 'mcpServers' for server definitions, not 'mcp'
s.mcpServers = s.mcpServers || {};
s.mcpServers['xed-ide'] = {
  url: 'http://127.0.0.1:' + idePort + '/mcp',
  headers: { Authorization: 'Bearer ' + ideToken }
};

// Cleanup any accidental 'xed-ide' entry in the 'mcp' object
if (s.mcp) { delete s.mcp['xed-ide']; }

fs.writeFileSync(settingsFile, JSON.stringify(s, null, 2));
NODE
  fi
  info "Xed Editor IDE bridge configured for Gemini on port $IDE_PORT"
  if [ -n "$BRIDGE_OK" ]; then
    info "Bridge health check passed"
  fi
}

ensure_node
configure_xed_ide_integration
ensure_gemini_cli

info "Starting Gemini CLI in $(pwd)"
exec gemini "$@"
