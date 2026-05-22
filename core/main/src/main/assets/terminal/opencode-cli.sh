#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

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

info() { log "[INFO] $*"; }
warn() { log "[WARN] $*"; }

configure_ai_auth_browser

ensure_node() {
  if ! command_exists node || ! command_exists npm; then
    warn "Node.js/npm is required."
    if command_exists apt; then
        info "Attempting to install Node.js..."
        install_nodejs || true
    fi
  fi
  
  if ! command_exists node; then
    error "Node.js is not installed and could not be auto-installed."
    error "Please run: apt update && apt install nodejs"
    exit 1
  fi
}

info "Starting OpenCode CLI..."
info "Workspace: $WKDIR"

# Wire with Xed Editor IDE bridge via MCP (merge with existing config)
configure_xed_ide_integration() {
  [ -n "$IDE_PORT" ] || return 0
  [ -n "$IDE_TOKEN" ] || return 0
  
  OPENCODE_CONFIG_DIR="$HOME/.config/opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  CONFIG_FILE="$OPENCODE_CONFIG_DIR/opencode.json"
  
  if command_exists python3; then
    python3 -c "
import json, os
port = os.environ.get('IDE_SERVER_PORT', '${IDE_PORT}')
token = os.environ.get('IDE_AUTH_TOKEN', '${IDE_TOKEN}')
try:
    with open('$CONFIG_FILE') as f: cfg = json.load(f)
except:
    cfg = {}
ms = cfg.setdefault('mcp', {})
ms['xed-ide'] = {
    'type': 'remote',
    'url': f'http://127.0.0.1:{port}/mcp?token={token}',
    'enabled': True,
    'timeout': 120000,
    'headers': {
        'Authorization': f'Bearer {token}',
        'authorization': f'Bearer {token}',
        'x-ide-token': token
    }
}
# OpenCode config schema is strict; remove legacy/invalid keys from old builds.
cfg.pop('mcpServers', None)
cfg.pop('apiKey', None)
with open('$CONFIG_FILE', 'w') as f: json.dump(cfg, f, indent=2)
" 2>/dev/null || fallback_to_node=true
  fi
  
  if [ "${fallback_to_node:-false}" = true ] || ! command_exists python3; then
    export IDE_SERVER_PORT IDE_AUTH_TOKEN
    node <<'NODE'
const fs = require('fs');
const os = require('os');
const path = require('path');
const configDir = path.join(os.homedir(), '.config', 'opencode');
if (!fs.existsSync(configDir)) fs.mkdirSync(configDir, { recursive: true });
const configFile = path.join(configDir, 'opencode.json');
const idePort = process.env.IDE_SERVER_PORT || '0';
const ideToken = process.env.IDE_AUTH_TOKEN || '';
let cfg = {};
try { cfg = JSON.parse(fs.readFileSync(configFile, 'utf8')); } catch (_) {}
cfg.mcp = cfg.mcp || {};
cfg.mcp['xed-ide'] = {
  type: 'remote',
  url: 'http://127.0.0.1:' + idePort + '/mcp?token=' + ideToken,
  enabled: true,
  timeout: 120000,
  headers: {
    Authorization: 'Bearer ' + ideToken,
    authorization: 'Bearer ' + ideToken,
    'x-ide-token': ideToken
  }
};
delete cfg.mcpServers;
delete cfg.apiKey;
fs.writeFileSync(configFile, JSON.stringify(cfg, null, 2));
NODE
  fi
  
  info "IDE bridge MCP configured for OpenCode on port $IDE_PORT (HTTP transport)"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    info "Bridge health check passed" || \
    warn "Bridge health check failed, MCP may be unavailable"
}

ensure_node
configure_xed_ide_integration

# Ensure OpenCode CLI is available
if ! command_exists opencode; then
  info "OpenCode CLI not found. Checking npm global list..."
  if npm list -g opencode-ai 2>/dev/null | grep -q opencode-ai; then
    warn "opencode-ai is in npm global list but 'opencode' command is not in PATH."
    warn "This usually means npm global bin directory is not in your PATH."
  else
    info "Installing OpenCode CLI..."
    npm install -g opencode-ai@latest || true
  fi
  
  if ! command_exists opencode; then
    info "Trying npx fallback..."
    exec npx --yes opencode-ai "$@"
  fi
fi

exec opencode "$@"
