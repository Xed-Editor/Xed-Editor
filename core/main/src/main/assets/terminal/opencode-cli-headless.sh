#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# Support both generic and Gemini-specific IDE bridge env vars
IDE_PORT="${IDE_SERVER_PORT:-${GEMINI_CLI_IDE_SERVER_PORT:-${XED_IDE_PORT:-}}}"
IDE_TOKEN="${IDE_AUTH_TOKEN:-${GEMINI_CLI_IDE_AUTH_TOKEN:-${XED_IDE_AUTH_TOKEN:-}}}"
IDE_WS="${IDE_WORKSPACE_PATH:-${GEMINI_CLI_IDE_WORKSPACE_PATH:-${XED_IDE_WORKSPACE_PATH:-}}}"

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

export NO_UPDATE_NOTIFIER=1
export PATH="$LOCAL/bin:$PATH"
export EDITOR=vim
export VISUAL=vim

log() { printf '%s\n' "$*" >&2; }

# Wire with Xed Editor IDE bridge via MCP (HTTP transport)
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  OPENCODE_CONFIG_DIR="$HOME/.config/opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  CONFIG_FILE="$OPENCODE_CONFIG_DIR/opencode.json"
  # Merge with existing config instead of overwriting
  if [ -f "$CONFIG_FILE" ]; then
    python3 -c "
import json, sys
with open('$CONFIG_FILE') as f:
    cfg = json.load(f)
ms = cfg.setdefault('mcp', {})
ms['xed-ide'] = {
    'type': 'remote',
    'url': 'http://127.0.0.1:${IDE_PORT}/mcp',
    'enabled': True,
    'headers': {
        'Authorization': 'Bearer ${IDE_TOKEN}',
        'authorization': 'Bearer ${IDE_TOKEN}',
        'x-ide-token': '${IDE_TOKEN}'
    }
}
with open('$CONFIG_FILE', 'w') as f:
    json.dump(cfg, f, indent=2)
" 2>/dev/null || {
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
    }
  else
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
  log "IDE bridge MCP configured for OpenCode on port $IDE_PORT (HTTP transport)"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    log "Bridge health check passed" || \
    log "Warning: bridge health check failed, MCP may be unavailable"
fi

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required. Installing Node.js LTS..."
  install_nodejs >/dev/null 2>&1
fi

if ! command -v opencode >/dev/null 2>&1; then
  log "Installing OpenCode CLI..."
  npm install -g opencode-ai@latest >/dev/null 2>&1
fi

exec opencode "$@"
