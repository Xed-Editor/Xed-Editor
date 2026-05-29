#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

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

info() { log "[INFO] $*"; }
warn() { log "[WARN] $*"; }

info "Starting Antigravity CLI..."
info "Workspace: $WKDIR"

# Wire with Xed Editor IDE bridge via MCP (Antigravity uses mcp_config.json with serverUrl)
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  AGY_CONFIG_DIR="$HOME/.config/agy"
  mkdir -p "$AGY_CONFIG_DIR"
  MCP_CONFIG_FILE="$AGY_CONFIG_DIR/mcp_config.json"
  cat > "$MCP_CONFIG_FILE" << AGY_MCP
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
  info "IDE bridge MCP configured for Antigravity on port $IDE_PORT"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    info "Bridge health check passed" || \
    warn "Bridge health check failed, MCP may be unavailable"
fi

# Antigravity is a Go native binary — no Node.js needed
if ! command -v agy >/dev/null 2>&1; then
  info "Installing Antigravity CLI..."
  TMP_SCRIPT="$(mktemp)"
  curl -fsSL https://antigravity.google/cli/install.sh > "$TMP_SCRIPT"
  # Replace the 'install' subcommand (which crashes with TCMalloc OOM in sandboxed envs)
  # with a no-op so the binary is just copied to the target dir
  sed -i 's/"$BINARY_PATH" install.*|| true/true # install skipped/' "$TMP_SCRIPT"
  bash "$TMP_SCRIPT" --dir "$LOCAL/bin" && chmod +x "$LOCAL/bin/agy" 2>/dev/null || true
  rm -f "$TMP_SCRIPT"
fi

info "Starting Antigravity CLI in $(pwd)"
exec agy "$@"
