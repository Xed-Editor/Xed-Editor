#!/usr/bin/env bash
# Android/Termux-compatible URL opener used by Gemini CLI OAuth.
# Gemini CLI tries to spawn xdg-open on Linux. In Xed's proot Ubuntu there is
# usually no desktop xdg-open, so bridge URL opening to Android instead.
set -u

url="${1:-}"
if [ -z "$url" ]; then
  echo "xdg-open: missing URL" >&2
  exit 1
fi

if command -v termux-open-url >/dev/null 2>&1; then
  exec termux-open-url "$url"
fi

if [ -x /system/bin/am ]; then
  exec /system/bin/am start -a android.intent.action.VIEW -d "$url" >/dev/null 2>&1
fi

# Last-resort fallback: print the URL so the user can copy it.
echo "$url" >&2
exit 1
