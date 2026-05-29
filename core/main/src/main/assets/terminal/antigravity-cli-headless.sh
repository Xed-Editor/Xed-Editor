#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# Support both agent-specific and generic IDE bridge env vars
IDE_PORT="${ANTIGRAVITY_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-}}"
IDE_TOKEN="${ANTIGRAVITY_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-}}"
IDE_WS="${ANTIGRAVITY_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-}}"

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

export NO_UPDATE_NOTIFIER=1
export PATH="$LOCAL/bin:$PATH"
export EDITOR=vim
export VISUAL=vim

log() { printf '%s\n' "$*" >&2; }

# Wire with Xed Editor IDE bridge via MCP
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  mkdir -p "$HOME/.config/agy"
  cat > "$HOME/.config/agy/mcp_config.json" << AGY_MCP
{
  "mcpServers": {
    "xed-ide": {
      "serverUrl": "http://127.0.0.1:${IDE_PORT}/mcp",
      "headers": {
        "Authorization": "Bearer ${IDE_TOKEN}"
      }
    }
  }
}
AGY_MCP
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    log "IDE bridge MCP configured for Antigravity on port $IDE_PORT" || \
    log "Warning: bridge health check failed, MCP may be unavailable"
fi

# Antigravity is a Go native binary — no Node.js needed
if ! command -v agy >/dev/null 2>&1; then
  log "Installing Antigravity CLI..."
  curl -fsSL https://antigravity.google/cli/install.sh | bash -s -- --dir "$LOCAL/bin" >/dev/null 2>&1
fi

exec agy "$@"
