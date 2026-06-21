#!/usr/bin/env bash
set -e

source "$LOCAL/bin/utils"

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

info() { log "[INFO] $*"; }
warn() { log "[WARN] $*"; }

AGY_BIN="$LOCAL/bin/agy"

info "Starting Antigravity CLI..."
info "Workspace: $WKDIR"

# Configure the Xed Editor IDE bridge as an MCP server
configure_xed_mcp antigravity "$IDE_PORT" "$IDE_TOKEN"

# --- Install agy binary if not present via official installer ---
install_agy() {
  info "Installing Antigravity CLI from official source..."

  INSTALLER_URL="https://antigravity.google/cli/install.sh"
  STAGING_DIR="$HOME/.cache/antigravity/staging"
  mkdir -p "$STAGING_DIR" 2>/dev/null || { warn "Cannot create staging dir"; return 1; }
  local installer_sh="$STAGING_DIR/install.sh"

  cleanup() { rm -f "$installer_sh" 2>/dev/null || true; }
  trap cleanup EXIT

  info "Downloading official installer..."
  curl -fsSL -o "$installer_sh" "$INSTALLER_URL" 2>&1 || {
    warn "Failed to download installer from $INSTALLER_URL"
    return 1
  }

  info "Running official installer..."
  bash "$installer_sh" --dir "$LOCAL/bin" 2>&1 || {
    warn "Official installation failed"
    return 1
  }

  if [ -x "$AGY_BIN" ]; then
    info "Antigravity CLI installed successfully"
  else
    warn "Binary not found at $AGY_BIN after installation"
    return 1
  fi
}

# Install if missing
if [ ! -x "$AGY_BIN" ]; then
  install_agy || warn "Installation failed — Antigravity CLI will be unavailable"
fi

# Launch
if [ -x "$AGY_BIN" ]; then
  info "Starting Antigravity CLI in $(pwd)"
  exec "$AGY_BIN" "$@"
else
  warn "Antigravity CLI is not available."
  exit 1
fi
