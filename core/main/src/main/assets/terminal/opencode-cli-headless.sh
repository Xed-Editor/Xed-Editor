#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# IDE bridge env vars (set by Xed Editor)
IDE_PORT="${IDE_SERVER_PORT:-}"
IDE_TOKEN="${IDE_AUTH_TOKEN:-}"
IDE_WS="${IDE_WORKSPACE_PATH:-}"

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

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

# Wire with Xed Editor IDE bridge via MCP (HTTP transport)
configure_xed_ide_integration() {
  [ -n "$IDE_PORT" ] || return 0
  [ -n "$IDE_TOKEN" ] || return 0
  
  OPENCODE_CONFIG_DIR="$HOME/.opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  CONFIG_FILE="$OPENCODE_CONFIG_DIR/mcp.json"
  
  if command_exists python3; then
    python3 -c "
import json, os
port = os.environ.get('IDE_PORT', '${IDE_PORT}')
token = os.environ.get('IDE_TOKEN', '${IDE_TOKEN}')
try:
    with open('$CONFIG_FILE') as f: cfg = json.load(f)
except:
    cfg = {}
ms = cfg.setdefault('mcp', {})
ms['xed-ide'] = {
    'type': 'remote',
    'url': f'http://127.0.0.1:{port}/mcp',
    'enabled': True,
    'headers': {
        'Authorization': f'Bearer {token}',
        'authorization': f'Bearer {token}',
        'x-ide-token': token
    }
}
with open('$CONFIG_FILE', 'w') as f: json.dump(cfg, f, indent=2)
" 2>/dev/null || fallback_to_node=true
  fi
  
  if [ "${fallback_to_node:-false}" = true ] || ! command_exists python3; then
    export IDE_PORT IDE_TOKEN
    node <<'NODE'
const fs = require('fs');
const os = require('os');
const path = require('path');
const configDir = path.join(os.homedir(), '.config', 'opencode');
if (!fs.existsSync(configDir)) fs.mkdirSync(configDir, { recursive: true });
const configFile = path.join(configDir, 'opencode.json');
const idePort = process.env.IDE_PORT || '0';
const ideToken = process.env.IDE_TOKEN || '';
let cfg = {};
try { cfg = JSON.parse(fs.readFileSync(configFile, 'utf8')); } catch (_) {}
cfg.mcp = cfg.mcp || {};
cfg.mcp['xed-ide'] = {
  type: 'remote',
  url: 'http://127.0.0.1:' + idePort + '/mcp',
  enabled: true,
  headers: {
    Authorization: 'Bearer ' + ideToken,
    authorization: 'Bearer ' + ideToken,
    'x-ide-token': ideToken
  }
};
fs.writeFileSync(configFile, JSON.stringify(cfg, null, 2));
NODE
  fi
  
  log "IDE bridge MCP configured for OpenCode on port $IDE_PORT (HTTP transport)"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    log "Bridge health check passed" || \
    log "Warning: bridge health check failed, MCP may be unavailable"
}

configure_xed_ide_integration

# Ensure OpenCode CLI is available
if ! command -v opencode >/dev/null 2>&1; then
  # Check if it's installed but not in PATH
  if npm list -g opencode-ai 2>/dev/null | grep -q opencode-ai; then
    log "opencode is in npm global list but not in PATH."
    log "TIP: Run 'npm config set prefix \$LOCAL && npm install -g opencode-ai'"
    exit 1
  fi
  log "OpenCode CLI not found. Attempting one-time install..."
  npm install -g opencode-ai@latest 2>&1 | while IFS= read -r line; do log "$line"; done
  if ! command -v opencode >/dev/null 2>&1; then
    log "Failed to locate opencode after install. Trying npx..."
    exec npx --yes opencode-ai "$@"
  fi
fi

exec opencode "$@"
