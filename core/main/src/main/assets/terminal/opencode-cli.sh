#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

# Support both generic and Gemini-specific IDE bridge env vars
IDE_PORT="${IDE_SERVER_PORT:-${GEMINI_CLI_IDE_SERVER_PORT:-}}"
IDE_TOKEN="${IDE_AUTH_TOKEN:-${GEMINI_CLI_IDE_AUTH_TOKEN:-}}"
IDE_WS="${IDE_WORKSPACE_PATH:-${GEMINI_CLI_IDE_WORKSPACE_PATH:-}}"

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

# Wire with Xed Editor IDE bridge via MCP
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  OPENCODE_CONFIG_DIR="$HOME/.config/opencode"
  mkdir -p "$OPENCODE_CONFIG_DIR"
  cat > "$OPENCODE_CONFIG_DIR/opencode.json" << OC_CONFIG
{
  "mcp": {
    "xed-ide": {
      "type": "remote",
      "url": "http://127.0.0.1:${IDE_PORT}/sse",
      "enabled": true,
      "headers": {
        "Authorization": "Bearer ${IDE_TOKEN}"
      },
      "timeout": 10000
    }
  }
}
OC_CONFIG
  info "IDE bridge MCP configured for OpenCode on port $IDE_PORT"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    info "Bridge health check passed" || \
    warn "Bridge health check failed, MCP may be unavailable"
fi

ensure_node() {
  if ! command_exists node || ! command_exists npm; then
    warn "Node.js/npm is required. Installing..."
    install_nodejs
  fi
}

ensure_opencode() {
  if ! command_exists opencode; then
    info "Installing OpenCode CLI..."
    npm install -g opencode-ai@latest
    info "OpenCode CLI installed successfully."
  fi
}

ensure_node
ensure_opencode

exec opencode "$@"
