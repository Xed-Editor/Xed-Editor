#!/usr/bin/env bash
set -e

# Headless Gemini wrapper for in-editor AI actions.
# Important: stdout must contain only Gemini's answer, because the app may insert it into the editor.
source "$LOCAL/bin/utils" >/dev/null 2>&1 || true

cd "${WKDIR:-$HOME}" 2>/dev/null || cd "$HOME"

log() { printf '%s\n' "$*" >&2; }

if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
  log "Node.js/npm is required. Installing Node.js LTS..."
  install_nodejs >&2
fi

if ! command -v gemini >/dev/null 2>&1; then
  log "Installing Gemini CLI..."
  npm install -g --prefix /usr @google/gemini-cli >&2
fi

if [ -z "${GEMINI_API_KEY:-}" ] && [ -z "${GOOGLE_API_KEY:-}" ]; then
  log "No API key found. Headless mode will use any existing Gemini CLI Google login or cached auth. If auth is required, open the CLI button once and complete Gemini CLI login."
fi

exec gemini "$@"
