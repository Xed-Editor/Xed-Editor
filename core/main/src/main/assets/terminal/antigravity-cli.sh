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

AGY_TERMUX_URL="https://github.com/wallentx/antigravity-cli-termux/releases/latest/download/antigravity-termux-standalone.tar.gz"

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

# --- Install agy binary if not present (Termux-compatible build) ---
install_agy() {
  info "Installing Antigravity CLI (Termux build)..."

  STAGING_DIR="$HOME/.cache/antigravity/staging"
  mkdir -p "$STAGING_DIR"
  staging_payload="$STAGING_DIR/antigravity-termux.tar.gz"

  cleanup() { rm -f "${staging_payload:-}" 2>/dev/null || true; }
  trap cleanup EXIT

  info "Downloading release package..."
  curl -fL -o "$staging_payload" "$AGY_TERMUX_URL" || { warn "Download failed"; return 1; }

  info "Extracting binaries..."
  tar -xzf "$staging_payload" -C "$STAGING_DIR" bin/agy bin/agy.va39 2>/dev/null || {
    warn "Extraction failed"; return 1
  }

  mkdir -p "$LOCAL/bin"
  cp "$STAGING_DIR/bin/agy" "$LOCAL/bin/agy" 2>/dev/null || { warn "Failed to copy agy"; return 1; }
  cp "$STAGING_DIR/bin/agy.va39" "$LOCAL/bin/agy.va39" 2>/dev/null || { warn "Failed to copy agy.va39"; return 1; }
  chmod +x "$LOCAL/bin/agy" "$LOCAL/bin/agy.va39" 2>/dev/null || true
  rm -rf "$STAGING_DIR"
  info "Installed agy + agy.va39 to $LOCAL/bin"
}

# Install if missing
if ! command -v agy >/dev/null 2>&1; then
  install_agy || warn "Installation failed — Antigravity CLI will be unavailable"
fi

# Launch
if command -v agy >/dev/null 2>&1; then
  info "Starting Antigravity CLI in $(pwd)"
  exec agy "$@"
else
  warn "Antigravity CLI is not available."
  exit 1
fi
