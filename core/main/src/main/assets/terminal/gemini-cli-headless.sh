#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# Support both Gemini-specific and generic IDE bridge env vars
IDE_PORT="${GEMINI_CLI_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-${XED_IDE_PORT:-}}}"
IDE_TOKEN="${GEMINI_CLI_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-${XED_IDE_AUTH_TOKEN:-}}}"
IDE_WS="${GEMINI_CLI_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-${XED_IDE_WORKSPACE_PATH:-}}}"

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
configure_ai_auth_browser

log() { printf '%s\n' "$*" >&2; }

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required. Installing Node.js LTS..."
  install_nodejs >/dev/null 2>&1
fi

# Wire with Xed Editor IDE bridge via MCP
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  BRIDGE_OK=$(curl -sf "http://127.0.0.1:${IDE_PORT}/health" 2>/dev/null || echo "")
  if [ -z "$BRIDGE_OK" ]; then
    log "Warning: IDE bridge not reachable on port $IDE_PORT"
  fi
  mkdir -p "$HOME/.gemini"
  SETTINGS_FILE="$HOME/.gemini/settings.json"
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
    'headers': {
        'Authorization': 'Bearer ${IDE_TOKEN}',
        'authorization': 'Bearer ${IDE_TOKEN}',
        'x-ide-token': '${IDE_TOKEN}'
    }
}

# Cleanup any accidental 'xed-ide' entry in the 'mcp' object (used for global settings)
if 'mcp' in s: s['mcp'].pop('xed-ide', None)

with open('$SETTINGS_FILE', 'w') as f:
    json.dump(s, f, indent=2)
" 2>/dev/null || fallback_to_node=true
  fi
  if [ "${fallback_to_node:-false}" = true ] || ! command_exists python3; then
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
  headers: { 
    Authorization: 'Bearer ' + ideToken,
    authorization: 'Bearer ' + ideToken,
    'x-ide-token': ideToken
  }
};

// Cleanup any accidental 'xed-ide' entry in the 'mcp' object
if (s.mcp) { delete s.mcp['xed-ide']; }

fs.writeFileSync(settingsFile, JSON.stringify(s, null, 2));
NODE
  fi
  log "IDE bridge MCP configured for Gemini on port $IDE_PORT"
fi

if ! command -v gemini >/dev/null 2>&1; then
  log "Installing Gemini CLI..."
  npm install -g --prefix /usr @google/gemini-cli >/dev/null 2>&1
fi

exec gemini "$@"
