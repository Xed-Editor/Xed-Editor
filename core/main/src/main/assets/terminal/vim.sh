#!/usr/bin/env bash
set -euo pipefail

if [ -z "${GEMINI_CLI_IDE_SERVER_PORT:-}" ] || [ -z "${GEMINI_CLI_IDE_AUTH_TOKEN:-}" ]; then
  echo "Xed external editor bridge is not available." >&2
  exit 1
fi

node - "$@" <<'NODE'
const fs = require('fs');
const http = require('http');
const path = require('path');

const args = process.argv.slice(2);
const existingFiles = args
  .filter((arg) => !arg.startsWith('-'))
  .map((arg) => path.resolve(arg))
  .filter((arg) => {
    try { return fs.existsSync(arg) && fs.statSync(arg).isFile(); } catch (_) { return false; }
  });

let oldPath = null;
let newPath = null;
if (args.includes('-d') && existingFiles.length >= 2) {
  oldPath = existingFiles[existingFiles.length - 2];
  newPath = existingFiles[existingFiles.length - 1];
} else if (existingFiles.length >= 1) {
  newPath = existingFiles[existingFiles.length - 1];
}

if (!newPath) {
  console.error('Xed external editor: no editable file path found.');
  process.exit(1);
}

const payload = JSON.stringify({ oldPath, newPath, args });
const request = http.request({
  host: '127.0.0.1',
  port: Number(process.env.GEMINI_CLI_IDE_SERVER_PORT),
  path: '/external-editor',
  method: 'POST',
  headers: {
    'Authorization': `Bearer ${process.env.GEMINI_CLI_IDE_AUTH_TOKEN}`,
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload),
  },
}, (response) => {
  let body = '';
  response.setEncoding('utf8');
  response.on('data', (chunk) => body += chunk);
  response.on('end', () => {
    if (response.statusCode < 200 || response.statusCode >= 300) {
      console.error(body || `Xed external editor failed: HTTP ${response.statusCode}`);
      process.exit(1);
    }
    try {
      const result = JSON.parse(body || '{}');
      process.exit(result.accepted ? 0 : 1);
    } catch (_) {
      process.exit(0);
    }
  });
});
request.on('error', (error) => {
  console.error(`Xed external editor bridge error: ${error.message}`);
  process.exit(1);
});
request.write(payload);
request.end();
NODE
