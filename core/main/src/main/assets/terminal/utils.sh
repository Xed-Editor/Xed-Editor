RESET='\033[0m'

BOLD_BLUE='\033[1;34m'
BOLD_YELLOW='\033[1;33m'
BOLD_RED='\033[1;31m'

BLUE_BG='\033[1;44m'
YELLOW_BG='\033[1;43m'
RED_BG='\033[1;41m'

info() {
  printf "\n${BLUE_BG}  INFO  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$1"
}

warn() {
  printf "\n${YELLOW_BG}  WARN  ${RESET} ${BOLD_YELLOW}%s${RESET}\n" "$1"
}

error() {
  printf "\n${RED_BG} ERROR ${RESET} ${BOLD_RED}%s${RESET}\n" "$1"
}

ask() {
  local prompt="$1"
  local response

  while true; do
    printf "\n${BLUE_BG}  ?  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$prompt"
    read -rp "[y/N]: " response
    case "$response" in
      [Yy]|[Yy][Ee][Ss])
        return 0
        ;;
      [Nn]|[Nn][Oo]|"")
        return 1
        ;;
      *)
        warn "Please answer yes or no."
        ;;
    esac
  done
}

ensure_node() {
  if ! command_exists node || ! command_exists npm; then
    warn "Node.js/npm is required. Installing..."
    install_nodejs
  fi
}

install_nodejs() {
  info "Installing Node.js LTS..."
  apt install -y curl ca-certificates
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt install -y nodejs
}

uninstall_nodejs() {
  if ask "Do you want to uninstall Node.js LTS? It was installed as a dependency of this language server. This will also remove all globally installed npm packages."; then
    info "Uninstalling Node.js LTS..."
    apt remove -y nodejs
    apt autoremove -y
    info "Node.js LTS uninstalled successfully."
  fi
}

command_exists() {
  command -v "$1" >/dev/null 2>&1
}


xed_mcp_log() {
  if command_exists info; then
    info "$1"
  elif command_exists log; then
    log "$1"
  else
    printf '%s\n' "$1" >&2
  fi
}

xed_mcp_warn() {
  if command_exists warn; then
    warn "$1"
  elif command_exists log; then
    log "Warning: $1"
  else
    printf 'Warning: %s\n' "$1" >&2
  fi
}

xed_mcp_health_check() {
  local port="$1"
  curl -sf "http://127.0.0.1:${port}/health" >/dev/null 2>&1
}

configure_xed_mcp() {
  local agent="$1"
  local port="$2"
  local token="$3"

  [ -n "$agent" ] || return 0
  [ -n "$port" ] || return 0
  [ -n "$token" ] || return 0

  export IDE_SERVER_PORT="$port"
  export IDE_AUTH_TOKEN="$token"
  export MCP_HOST="127.0.0.1"
  export MCP_PORT="$port"
  export MCP_AUTH_TOKEN="$token"

  case "$agent" in
    gemini)
      export GEMINI_CLI_IDE_SERVER_PORT="$port"
      export GEMINI_CLI_IDE_AUTH_TOKEN="$token"
      mkdir -p "$HOME/.gemini"
      XED_MCP_PORT="$port" XED_MCP_TOKEN="$token" XED_MCP_FILE="$HOME/.gemini/settings.json" python3 - <<'PY_MCP' 2>/dev/null || return 0
import json, os
path = os.environ['XED_MCP_FILE']
port = os.environ['XED_MCP_PORT']
token = os.environ['XED_MCP_TOKEN']
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    cfg = {}
cfg.setdefault('general', {})['preferredEditor'] = 'vim'
cfg.setdefault('ide', {})['enabled'] = True
cfg.setdefault('ide', {})['hasSeenNudge'] = True
cfg.setdefault('privacy', {})['usageStatisticsEnabled'] = False
cfg.setdefault('telemetry', {})['enabled'] = False
cfg.setdefault('mcpServers', {})['xed-ide'] = {
    'url': f'http://127.0.0.1:{port}/mcp',
    'headers': {'Authorization': f'Bearer {token}'},
}
if isinstance(cfg.get('mcp'), dict):
    cfg['mcp'].pop('xed-ide', None)
with open(path, 'w') as f:
    json.dump(cfg, f, indent=2)
PY_MCP
      ;;
    opencode)
      mkdir -p "$HOME/.config/opencode"
      XED_MCP_PORT="$port" XED_MCP_TOKEN="$token" XED_MCP_FILE="$HOME/.config/opencode/opencode.json" python3 - <<'PY_MCP' 2>/dev/null || return 0
import json, os
path = os.environ['XED_MCP_FILE']
port = os.environ['XED_MCP_PORT']
token = os.environ['XED_MCP_TOKEN']
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    cfg = {}
if isinstance(cfg.get('mcpServers'), dict):
    cfg['mcpServers'].pop('xed-ide', None)
cfg.setdefault('mcp', {})['xed-ide'] = {
    'type': 'remote',
    'url': f'http://127.0.0.1:{port}/mcp',
    'enabled': True,
    'headers': {'Authorization': f'Bearer {token}'},
}
with open(path, 'w') as f:
    json.dump(cfg, f, indent=2)
PY_MCP
      ;;
    antigravity)
      export ANTIGRAVITY_IDE_SERVER_PORT="$port"
      export ANTIGRAVITY_IDE_AUTH_TOKEN="$token"
      mkdir -p "$HOME/.config/agy"
      XED_MCP_PORT="$port" XED_MCP_TOKEN="$token" XED_MCP_FILE="$HOME/.config/agy/mcp_config.json" python3 - <<'PY_MCP' 2>/dev/null || return 0
import json, os
path = os.environ['XED_MCP_FILE']
port = os.environ['XED_MCP_PORT']
token = os.environ['XED_MCP_TOKEN']
try:
    with open(path) as f:
        cfg = json.load(f)
except Exception:
    cfg = {}
cfg.setdefault('mcpServers', {})['xed-ide'] = {
    'serverUrl': f'http://127.0.0.1:{port}/mcp',
    'url': f'http://127.0.0.1:{port}/mcp',
    'type': 'remote',
    'enabled': True,
    'headers': {'Authorization': f'Bearer {token}'},
}
with open(path, 'w') as f:
    json.dump(cfg, f, indent=2)
PY_MCP
      ;;
    codex)
      export CODEX_IDE_SERVER_PORT="$port"
      export CODEX_IDE_AUTH_TOKEN="$token"
      mkdir -p "$HOME/.codex"
      XED_MCP_PORT="$port" XED_MCP_TOKEN="$token" XED_MCP_FILE="$HOME/.codex/config.toml" python3 - <<'PY_MCP' 2>/dev/null || return 0
import os
path = os.environ['XED_MCP_FILE']
port = os.environ['XED_MCP_PORT']
token = os.environ['XED_MCP_TOKEN']
url = f'http://127.0.0.1:{port}/mcp'
section = '[mcp_servers.xed-ide]'
replacement = [
    section,
    f'url = "{url}"',
    f'http_headers = {{ Authorization = "Bearer {token}" }}',
]
try:
    with open(path) as f:
        lines = f.read().splitlines()
except Exception:
    lines = []
out = []
i = 0
replaced = False
while i < len(lines):
    if lines[i].strip() == section:
        if out and out[-1].strip():
            out.append('')
        out.extend(replacement)
        replaced = True
        i += 1
        while i < len(lines) and not lines[i].lstrip().startswith('['):
            i += 1
        continue
    out.append(lines[i])
    i += 1
if not replaced:
    if out and out[-1].strip():
        out.append('')
    out.extend(replacement)
with open(path, 'w') as f:
    f.write('\n'.join(out).rstrip() + '\n')
PY_MCP
      ;;
    *)
      return 0
      ;;
  esac

  if xed_mcp_health_check "$port"; then
    xed_mcp_log "IDE bridge MCP configured for $agent on port $port"
  else
    xed_mcp_warn "Bridge health check failed for $agent on port $port; MCP may be unavailable"
  fi
}

configure_gemini_auth_browser() {
  # Gemini CLI OAuth tries xdg-open on Linux. Xed runs the CLI inside a
  # proot/Ubuntu environment on Android, where desktop xdg-open is usually
  # missing. Prefer our Android bridge xdg-open shim when available; otherwise
  # force Gemini into manual browser mode so it prints the auth URL cleanly
  # instead of failing with "spawn xdg-open ENOENT".
  if [ -n "${LOCAL:-}" ] && [ -x "$LOCAL/bin/xdg-open" ]; then
    export BROWSER="$LOCAL/bin/xdg-open"
    return 0
  fi

  if ! command_exists xdg-open && ! command_exists open; then
    export NO_BROWSER=true
    warn "No browser opener found. Gemini auth will print a URL; open it in Android browser, then return here."
  fi
}
