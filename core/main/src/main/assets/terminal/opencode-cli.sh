#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

# Support both generic and OpenCode-specific IDE bridge env vars
IDE_PORT="${OPENCODE_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-${XED_IDE_PORT:-}}}"
IDE_TOKEN="${OPENCODE_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-${XED_IDE_AUTH_TOKEN:-}}}"
IDE_WS="${OPENCODE_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-${XED_IDE_WORKSPACE_PATH:-}}}"

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

info "Starting OpenCode CLI..."
info "Workspace: $WKDIR"

# Wire with Xed Editor IDE bridge via MCP (merge with existing config)
# Note: Bridge config is primarily written by DiscoveryFileWriter (Java/Kotlin side).
# This script only ensures it's present as a fallback, preserving all existing fields.
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  OPENCODE_CONFIG_DIR="$HOME/.config/opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  CONFIG_FILE="$OPENCODE_CONFIG_DIR/opencode.json"
  if command_exists python3; then
    python3 -c "
import json, os
port = os.environ.get('IDE_PORT', '${IDE_PORT}')
token = os.environ.get('IDE_AUTH_TOKEN', '${IDE_TOKEN}')
cfg = json.load(open('$CONFIG_FILE')) if os.path.exists('$CONFIG_FILE') else {}
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
json.dump(cfg, open('$CONFIG_FILE', 'w'), indent=2)
" 2>/dev/null || warn "Failed to write MCP config (python3 merge error)"
  elif [ ! -f "$CONFIG_FILE" ]; then
    cat > "$CONFIG_FILE" << OC_CONFIG
{
  "mcp": {
    "xed-ide": {
      "type": "remote",
      "url": "http://127.0.0.1:${IDE_PORT}/mcp",
      "enabled": true,
      "headers": {
        "Authorization": "Bearer ${IDE_TOKEN}",
        "authorization": "Bearer ${IDE_TOKEN}",
        "x-ide-token": "${IDE_TOKEN}"
      }
    }
  }
}
OC_CONFIG
  fi
  info "IDE bridge MCP configured for OpenCode on port $IDE_PORT (HTTP transport)"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    info "Bridge health check passed" || \
    warn "Bridge health check failed, MCP may be unavailable"
fi

# Ensure Node.js is available
if ! command_exists node || ! command_exists npm; then
  warn "Node.js/npm is required for OpenCode CLI."
  info "Run: apt update && apt install -y nodejs"
  info "Then: npm install -g opencode-ai@latest"
  exit 1
fi

# Ensure OpenCode CLI is available
if ! command_exists opencode; then
  info "OpenCode CLI not found. Trying npm global check..."
  if npm list -g opencode-ai 2>/dev/null | grep -q opencode-ai; then
    warn "opencode is in npm global list but not in PATH."
    info "Run: npm config set prefix \$LOCAL && npm install -g opencode-ai"
    exit 1
  fi
  info "Installing OpenCode CLI..."
  npm install -g opencode-ai@latest
  if ! command_exists opencode; then
    warn "Installation completed but 'opencode' not in PATH."
    info "Using npx as fallback..."
    exec npx --yes opencode-ai "$@"
  fi
  info "OpenCode CLI installed successfully."
fi

exec opencode "$@"
