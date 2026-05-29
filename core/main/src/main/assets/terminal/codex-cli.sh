#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

# Support both agent-specific and generic IDE bridge env vars
IDE_PORT="${CODEX_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-}}"
IDE_TOKEN="${CODEX_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-}}"
IDE_WS="${CODEX_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-}}"

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

info "Starting Codex CLI..."
info "Workspace: $WKDIR"

ensure_node

if ! command -v codex >/dev/null 2>&1; then
  info "Installing Codex CLI..."
  npm install -g @openai/codex
fi

# Register the Xed-Editor IDE bridge as an MCP server for Codex
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  codex mcp add xed-ide \
    --url "http://127.0.0.1:${IDE_PORT}/mcp" \
    --bearer-token-env-var IDE_AUTH_TOKEN \
    2>/dev/null || true
  info "IDE bridge MCP configured for Codex on port $IDE_PORT"
  curl -sf "http://127.0.0.1:${IDE_PORT}/health" >/dev/null 2>&1 && \
    info "Bridge health check passed" || \
    warn "Bridge health check failed, MCP may be unavailable"
fi

info "Starting Codex CLI in $(pwd)"
exec codex "$@"
