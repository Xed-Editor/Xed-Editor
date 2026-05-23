## Xed-Editor

<img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="90" height="90" align="left"/>

**Xed-Editor** is a versatile and extensible text editor for Android, featuring syntax highlighting,
LSP-powered code intelligence, a built-in terminal, extensions, and fast project-wide tools for
efficient editing. This fork deeply integrates **AI agents** (Gemini CLI & OpenCode) with a full
**MCP (Model Context Protocol) IDE bridge**, giving them autonomous code editing capabilities.

![Android CI](https://github.com/algospider/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge)
![Download count](https://img.shields.io/github/downloads/algospider/Xed-Editor/total?label=Downloads)

---

## Features

- **Dual AI Agent Support**: Switch seamlessly between **Gemini CLI** and **OpenCode** agents within a unified sheet interface.
- **Full MCP IDE Bridge**: AI agents communicate over a standard MCP protocol (HTTP + SSE) with 30+ tools — read/write files, run terminal commands, search code, LSP navigation, git operations, project structure, and more.
- **Diff Review**: All AI-driven edits go through a side-by-side diff view — preview and approve/reject before changes are applied.
- **Inline Code Completion**: AI-powered inline completions via Gemini or OpenAI-compatible APIs.
- **Advanced Editing**: Powered by [Sora Editor](https://github.com/Rosemoe/sora-editor), supporting large files and smooth performance.
- **Code Intelligence**: Full LSP support for code completion, diagnostics, definitions, references, and rename.
- **Built-in Terminal**: Integrated terminal emulator based on Termux with proot/Ubuntu sandbox.
- **Extension System**: Customize with community-driven extensions and icon packs.
- **Modern UI**: Fully built with Jetpack Compose, clean Material 3 design.
- **Project Management**: File drawer with multi-file selection, Git status integration, and fast search.
- **Enhanced Productivity**: Color picker, minimap, fullscreen mode, and extensive customization.

---

## AI Agents Integration

### Gemini CLI
- Powered by `@google/gemini-cli` (npm)
- Default models: `gemini-2.5-flash` (fast) / `gemini-2.5-pro` (pro)
- Interactive terminal sheet with full IDE context awareness

### OpenCode
- Powered by `opencode-ai` (npm)
- Switchable via agent profile dropdown in the AI sheet
- Same MCP bridge access as Gemini

### IDE Bridge (MCP Server)
The embedded NanoHTTPD server exposes an MCP protocol with two transport modes:

| Endpoint | Purpose |
|----------|---------|
| `GET /health` | Health check |
| `GET /context` | IDE context (open files, workspace) |
| `GET /mcp-info` | Bridge metadata |
| `GET /sse` | SSE stream for MCP events |
| `POST /mcp` | MCP JSON-RPC (tools/list, tools/call) |
| `POST /messages` | SSE response routing |

### Available MCP Tools (30+)

| Category | Tools |
|----------|-------|
| **File Operations** | `readFile`, `writeFile`, `createFile`, `deleteFile`, `renameFile`, `listFiles` |
| **Editor Control** | `openFile`, `getOpenFiles`, `getActiveFile`, `getSelection`, `replaceSelection`, `insertAtCursor`, `saveOpenFiles`, `refreshOpenEditors` |
| **Diff Review** | `openDiff`, `closeDiff`, `rejectDiff` |
| **Search** | `searchCode`, `findFiles` |
| **LSP Intelligence** | `getDiagnostics`, `findDefinitions`, `findReferences`, `renameSymbol`, `formatDocument` |
| **Git** | `getGitStatus`, `getGitDiff` |
| **System** | `getIdeInfo`, `runCommand`, `showMessage`, `getTerminalOutput`, `getProjectStructure`, `getProjectConfig`, `getSymbolUnderCursor` |

### Auto-Discovery
The bridge writes configuration for both agents automatically:
- **Gemini**: `~/.gemini/settings.json` with `mcpServers.xed-ide`
- **OpenCode**: `~/.config/opencode/opencode.json` with `mcp.xed-ide` remote
- **Workspace**: `.xed/ide.json`, `.xed/ide.env`, `.opencode/mcp.json`
- **Temp discovery**: `/tmp/xed-ide/*.json`

### Agent Profiles
Three default profiles (fully customizable):
- **Fast**: Gemini + `gemini-2.5-flash`
- **Pro**: Gemini + `gemini-2.5-pro`
- **OpenCode**: OpenCode + your configured model

---

## Technical Stack

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose + Material 3
- **Editor Engine**: [Sora Editor](https://github.com/Rosemoe/sora-editor)
- **Terminal Emulator**: Integrated [Termux](https://github.com/termux/termux-app) runtime with proot/Ubuntu sandbox
- **AI Bridge**: NanoHTTPD (MCP Server) — HTTP JSON-RPC + SSE transport
- **AI Agents**: Gemini CLI (`@google/gemini-cli`) & OpenCode (`opencode-ai`) via npm
- **Inline Completion**: OkHttp — Gemini / OpenAI-compatible APIs
- **Build System**: Gradle (Kotlin DSL)
- **Concurrency**: Kotlin Coroutines & Flow

---

## Documentation

Official docs: [https://xed-editor.github.io/Xed-Docs/](https://xed-editor.github.io/Xed-Docs/)

[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/6bKzcQRuef)
[![Telegram](https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/XedEditor)

---

## Download

- **Latest Builds**: [GitHub Actions](https://github.com/algospider/Xed-Editor/actions/)
- **Releases**: [GitHub Releases](https://github.com/algospider/Xed-Editor/releases)

---

## Screenshots

<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="32%" />
</div>
<div>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/04.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/05.jpg" width="32%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/06.jpg" width="32%" />
</div>

---

## Contributing

See [`/docs/CONTRIBUTING.md`](/docs/CONTRIBUTING.md).

---

## Contributors

<a href="https://github.com/algospider/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=algospider/Xed-Editor" />
</a>
