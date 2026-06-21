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

# Configure the Xed Editor IDE bridge as an MCP server
configure_xed_mcp antigravity "$IDE_PORT" "$IDE_TOKEN"

AGY_BIN="$LOCAL/bin/agy"

# --- Install agy binary if not present via official installer ---
install_agy() {
  log "Installing Antigravity CLI from official source..."

  INSTALLER_URL="https://antigravity.google/cli/install.sh"
  STAGING_DIR="$HOME/.cache/antigravity/staging"
  mkdir -p "$STAGING_DIR" 2>/dev/null || { log "Cannot create staging dir"; return 1; }
  local installer_sh="$STAGING_DIR/install.sh"

  cleanup() { rm -f "$installer_sh" 2>/dev/null || true; }
  trap cleanup EXIT

  log "Downloading official installer..."
  curl -fsSL -o "$installer_sh" "$INSTALLER_URL" 2>/dev/null || {
    log "Failed to download installer from $INSTALLER_URL"
    return 1
  }

  log "Running official installer..."
  bash "$installer_sh" --dir "$LOCAL/bin" 2>/dev/null || {
    log "Official installation failed"
    return 1
  }

  if [ -x "$AGY_BIN" ]; then
    log "Antigravity CLI installed successfully"
  else
    log "Binary not found at $AGY_BIN after installation"
    return 1
  fi
}

if [ ! -x "$AGY_BIN" ]; then
  install_agy || log "Installation failed — Antigravity CLI unavailable"
fi

if [ -x "$AGY_BIN" ]; then
  exec "$AGY_BIN" "$@"
else
  log "Antigravity CLI is not available."
  exit 1
fi
