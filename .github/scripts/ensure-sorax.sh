#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
cd "$ROOT_DIR"

SORAX_URL="${SORAX_URL:-https://github.com/algospider/soraX.git}"
SORAX_REF="${SORAX_REF:-4b761b316326ff412b83ebaf7afe623da27d33d2}"

has_sorax_modules() {
  [ -d soraX/editor ] && \
  [ -d soraX/oniguruma-native ] && \
  [ -d soraX/editor-lsp ] && \
  [ -d soraX/language-textmate ]
}

if has_sorax_modules; then
  echo "soraX is already initialized."
  exit 0
fi

echo "Initializing soraX editor engine..."

if [ -f .gitmodules ] && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git config submodule.soraX.url "$SORAX_URL" || true
  git submodule sync --recursive soraX || true
  git submodule update --init --recursive --depth=1 soraX || true
fi

if ! has_sorax_modules; then
  echo "Submodule update did not populate soraX; cloning fallback from ${SORAX_URL}."
  rm -rf soraX
  git clone --depth=1 "$SORAX_URL" soraX
  git -C soraX fetch --depth=1 origin "$SORAX_REF" || true
  git -C soraX checkout --detach "$SORAX_REF"
  git -C soraX submodule update --init --recursive --depth=1
fi

if ! has_sorax_modules; then
  echo "soraX initialization failed. Current soraX contents:" >&2
  find soraX -maxdepth 2 -type d 2>/dev/null | sort >&2 || true
  exit 1
fi

echo "soraX initialized successfully."
