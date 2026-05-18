#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

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

log() { printf '%s\n' "$*" >&2; }

# Wire with Xed Editor IDE bridge via MCP (HTTP transport)
# Note: Config is primarily written by DiscoveryFileWriter; this is a safety fallback.
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  OPENCODE_CONFIG_DIR="$HOME/.config/opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  CONFIG_FILE="$OPENCODE_CONFIG_DIR/opencode.json"
  if command -v python3 >/dev/null 2>&1; then
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
" 2>/dev/null || log "Warning: failed to merge MCP config"
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
  log "IDE bridge MCP configured for OpenCode on port $IDE_PORT (HTTP transport)"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    log "Bridge health check passed" || \
    log "Warning: bridge health check failed, MCP may be unavailable"
fi

# Ensure Node.js is available
if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required for OpenCode CLI."
  log "TIP: Run 'apt update && apt install -y nodejs' then 'npm install -g opencode-ai'"
  exit 1
fi

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
