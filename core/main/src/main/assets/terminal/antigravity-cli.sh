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

DOWNLOAD_BASE_URL="https://antigravity-cli-auto-updater-974169037036.us-central1.run.app"

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

# --- Install agy binary if not present ---
install_agy() {
  info "Installing Antigravity CLI..."

  # Detect platform
  case "$(uname -s)" in
    Darwin) os="darwin" ;;
    Linux)  os="linux" ;;
    *)      warn "Unsupported OS: $(uname -s)"; return 1 ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64) arch="amd64" ;;
    arm64|aarch64) arch="arm64" ;;
    *)      warn "Unsupported arch: $(uname -m)"; return 1 ;;
  esac
  if [ "$os" = "linux" ]; then
    if [ -f /lib/libc.musl-x86_64.so.1 ] || [ -f /lib/libc.musl-aarch64.so.1 ] || ldd /bin/ls 2>&1 | grep -q musl; then
      platform="linux_${arch}_musl"
    else
      platform="linux_${arch}"
    fi
  else
    platform="${os}_${arch}"
  fi

  # Fetch manifest
  MANIFEST_URL="$DOWNLOAD_BASE_URL/manifests/$platform.json"
  manifest_json="$(curl -fsSL "$MANIFEST_URL" 2>/dev/null || true)"
  if [ -z "$manifest_json" ]; then
    warn "Failed to fetch release manifest from $MANIFEST_URL"
    return 1
  fi

  version="$(echo "$manifest_json" | sed -n 's/.*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  url="$(echo "$manifest_json" | sed -n 's/.*"url"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  sha512="$(echo "$manifest_json" | sed -n 's/.*"sha512"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"
  if [ -z "$url" ] || [ -z "$sha512" ]; then
    warn "Failed to parse release manifest"
    return 1
  fi
  info "Latest version: $version"

  # Download
  STAGING_DIR="$HOME/.cache/antigravity/staging"
  mkdir -p "$STAGING_DIR"
  is_tar_gz=false
  case "$url" in *.tar.gz*) is_tar_gz=true ;; esac
  if [ "$is_tar_gz" = true ]; then
    staging_payload="$STAGING_DIR/agy.tar.gz"
    extracted_binary="$STAGING_DIR/antigravity"
  else
    staging_payload="$STAGING_DIR/agy"
    extracted_binary="$staging_payload"
  fi
  cleanup() { rm -f "${staging_payload:-}" "${extracted_binary:-}" 2>/dev/null || true; }
  trap cleanup EXIT

  info "Downloading release package..."
  curl -fsSL -o "$staging_payload" "$url" || { warn "Download failed"; return 1; }

  # Verify checksum
  actual_hash="$(sha512sum "$staging_payload" | cut -d' ' -f1 || true)"
  if [ "$actual_hash" != "$sha512" ]; then
    warn "Checksum mismatch — expected $sha512, got $actual_hash"
    return 1
  fi
  info "Download verified (SHA512)"

  # Extract / copy to target
  mkdir -p "$LOCAL/bin"
  if [ "$is_tar_gz" = true ]; then
    info "Extracting binary..."
    tar -xzf "$staging_payload" -C "$STAGING_DIR" antigravity 2>/dev/null || {
      warn "Extraction failed"; return 1
    }
  fi
  cp "$extracted_binary" "$LOCAL/bin/agy" 2>/dev/null || { warn "Failed to copy binary"; return 1; }
  chmod +x "$LOCAL/bin/agy" 2>/dev/null || true
  info "Installed to $LOCAL/bin/agy"
}

# Install if missing
if ! command -v agy >/dev/null 2>&1; then
  install_agy || warn "Installation failed — Antigravity CLI will be unavailable"
fi

# Test if binary actually runs (TCMalloc may OOM in sandboxed envs)
if command -v agy >/dev/null 2>&1; then
  if agy --version >/dev/null 2>&1; then
    info "Starting Antigravity CLI in $(pwd)"
    exec agy "$@"
  else
    warn "Antigravity CLI binary was installed but cannot start in this environment."
    warn "The binary requires ~1GB virtual address space (TCMalloc), which is unavailable"
    warn "in the current sandbox. To use Antigravity, run on a device without VAS limits."
    exit 1
  fi
else
  warn "Antigravity CLI is not available."
  exit 1
fi
