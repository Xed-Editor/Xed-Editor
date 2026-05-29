#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

# Support both agent-specific and generic IDE bridge env vars
IDE_PORT="${ANTIGRAVITY_IDE_SERVER_PORT:-${IDE_SERVER_PORT:-}}"
IDE_TOKEN="${ANTIGRAVITY_IDE_AUTH_TOKEN:-${IDE_AUTH_TOKEN:-}}"
IDE_WS="${ANTIGRAVITY_IDE_WORKSPACE_PATH:-${IDE_WORKSPACE_PATH:-}}"

if [ -n "$IDE_TOKEN" ]; then
  export ANTIGRAVITY_IDE_AUTH_TOKEN="$IDE_TOKEN"
  export IDE_AUTH_TOKEN="$IDE_TOKEN"
fi

workspace_dir="${IDE_WS%%:*}"
target_dir="${WKDIR:-${workspace_dir:-$HOME}}"
cd "$target_dir" 2>/dev/null || cd "$workspace_dir" 2>/dev/null || cd "$HOME"
export WKDIR="$(pwd)"

export NO_UPDATE_NOTIFIER=1
export PATH="$LOCAL/bin:$PATH"
export EDITOR=vim
export VISUAL=vim

log() { printf '%s\n' "$*" >&2; }

AGY_TERMUX_URL="https://github.com/wallentx/antigravity-cli-termux/releases/latest/download/antigravity-termux-standalone.tar.gz"

# Wire with Xed Editor IDE bridge via MCP
if [ -n "$IDE_PORT" ] && [ -n "$IDE_TOKEN" ]; then
  mkdir -p "$HOME/.config/agy"
  cat > "$HOME/.config/agy/mcp_config.json" << AGY_MCP
{
  "mcpServers": {
    "xed-ide": {
      "serverUrl": "http://127.0.0.1:${IDE_PORT}/mcp",
      "url": "http://127.0.0.1:${IDE_PORT}/mcp",
      "type": "remote",
      "enabled": true,
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

# --- Install agy binary if not present ---
# Note: agy.va39 is a standard Linux Go ELF (the actual Antigravity CLI).
# agy is a small Android NDK helper (update checker) that only runs on
# Termux/host. Inside Xed's proot sandbox we run agy.va39 directly.
install_agy() {
  log "Installing Antigravity CLI..."

  STAGING_DIR="$HOME/.cache/antigravity/staging"
  mkdir -p "$STAGING_DIR"
  staging_payload="$STAGING_DIR/antigravity-termux.tar.gz"

  cleanup() { rm -f "${staging_payload:-}" 2>/dev/null || true; }
  trap cleanup EXIT

  log "Downloading release package..."
  curl -fL -o "$staging_payload" "$AGY_TERMUX_URL" 2>/dev/null || { log "Download failed"; return 1; }

  log "Extracting binaries..."
  tar -xzf "$staging_payload" -C "$STAGING_DIR" bin/agy.va39 bin/agy 2>/dev/null || {
    log "Extraction failed"; return 1
  }

  mkdir -p "$LOCAL/bin"
  cp "$STAGING_DIR/bin/agy.va39" "$LOCAL/bin/agy.va39" 2>/dev/null || { log "Failed to copy agy.va39"; return 1; }
  chmod +x "$LOCAL/bin/agy.va39" 2>/dev/null || true
  rm -rf "$STAGING_DIR"
  log "Installed agy.va39 to $LOCAL/bin"
}

AGY_BIN="$LOCAL/bin/agy.va39"
if [ ! -x "$AGY_BIN" ]; then
  install_agy || log "Installation failed — Antigravity CLI unavailable"
fi

if [ -x "$AGY_BIN" ]; then
  exec "$AGY_BIN" "$@"
else
  log "Antigravity CLI is not available."
  exit 1
fi
