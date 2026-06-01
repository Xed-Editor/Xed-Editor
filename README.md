<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="128" height="128" />
</div>

<h1 align="center">Xed-Editor</h1>
<h3 align="center">The AI-Powered Android Code Editor</h3>

<p align="center">
  <b>Versatile. Extensible. Intelligent.</b>
</p>

<p align="center">
  <a href="https://github.com/algospider/Xed-Editor/actions/workflows/android.yml">
    <img src="https://github.com/algospider/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push" alt="Android CI" />
  </a>
  <a href="https://github.com/algospider/Xed-Editor/releases">
    <img src="https://img.shields.io/github/downloads/algospider/Xed-Editor/total?label=Downloads&color=blue" alt="Download count" />
  </a>
  <a href="https://discord.gg/6bKzcQRuef">
    <img src="https://img.shields.io/badge/Discord-5865F2?logo=discord&logoColor=white" alt="Discord" />
  </a>
  <a href="https://t.me/XedEditor">
    <img src="https://img.shields.io/badge/Telegram-26A5E4?logo=telegram&logoColor=white" alt="Telegram" />
  </a>
  <a href="https://github.com/algospider/Xed-Editor/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-GPLv3-green" alt="License" />
  </a>
</p>

<br />

Xed-Editor is a high-performance, AI-powered code editor for Android, built 100% in Kotlin with Jetpack Compose. It combines a full-featured code editor (SoraX), a Termux-based terminal emulator, LSP code intelligence, and a quad-agent AI architecture with a Model Context Protocol (MCP) IDE bridge -- giving AI agents autonomous code editing capabilities directly on your device.

<br />

---

## Table of Contents

| | |
|:---|---|
| [Screenshots](#screenshots) | [Features](#features) |
| [AI Agent Architecture](#ai-agent-architecture) | [MCP IDE Bridge](#mcp-ide-bridge) |
| [Technical Stack](#technical-stack) | [Module Architecture](#module-architecture) |
| [Getting Started](#getting-started) | [Build Instructions](#build-instructions) |
| [Plugin SDK](#plugin-sdk) | [Contributing](#contributing) |
| [License](#license) | |

---

## Screenshots

<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="30%" />
  <br/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/04.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/05.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/06.jpg" width="30%" />
</div>

---

## Features

### AI Agent Integration

| | Feature | Description |
|:---:|:---|:---|
| | **Quad-Agent Support** | Seamlessly toggle between Gemini CLI, OpenCode, Antigravity CLI, and Codex CLI agents. |
| | **Agent Profiles** | Save, load, and apply named agent configurations with custom arguments. |
| | **Project Config** | Per-workspace `.xed/agent.json` for project-specific agent settings. |
| | **Pluggable Framework** | `AiAgent` interface + `AgentTypeRegistry` for easy addition of new AI backends. |

### MCP IDE Bridge

| | Feature | Description |
|:---:|:---|:---|
| | **30+ Tools** | Read/write files, run commands, LSP operations, git operations, search, and more. |
| | **NanoHTTPD Server** | Lightweight HTTP server implementing the Model Context Protocol with SSE support. |
| | **Auto-Discovery** | Agents auto-connect via JSON discovery files written to well-known paths. |
| | **PID Cleanup** | Stale bridge files are automatically cleaned up using PID tracking. |

### Code Editor

| | Feature | Description |
|:---:|:---|:---|
| | **SoraX Engine** | High-performance editor with syntax highlighting via TextMate grammars. |
| | **Minimap** | Scrollable minimap for quick file navigation. |
| | **Multi-Cursor** | Full multi-cursor and selection support. |
| | **Code Folding** | Collapse/expand code blocks. |
| | **Auto-Close** | Automatic bracket and quote completion. |
| | **Color Picker** | Built-in color picker for CSS/hex colors. |
| | **Read-Only Mode** | Toggle read-only per file. |

### Code Intelligence (LSP)

| | Feature | Description |
|:---:|:---|:---|
| | **Built-in Servers** | Bash, CSS, Emmet, ESLint, HTML, JSON, Markdown, Python, TypeScript/JavaScript, XML. |
| | **External Servers** | Connect to any LSP-compliant server via socket or process. |
| | **Diagnostics** | Real-time error and warning highlighting. |
| | **Completion** | Intelligent code completion with documentation. |
| | **Navigation** | Go to definition, find references, rename symbol. |
| | **Hover Info** | Type information and documentation on hover. |

### Terminal

| | Feature | Description |
|:---:|:---|:---|
| | **Termux Engine** | Full terminal emulator based on Termux. |
| | **Proot/Ubuntu** | Sandboxed Linux environment via proot. |
| | **Extra Keys** | Customizable virtual function keys. |
| | **Session Management** | Multiple terminal sessions with tab switching. |
| | **Shell Scripts** | Pre-installed launchers for all AI agents. |

### Git

| | Feature | Description |
|:---:|:---|:---|
| | **JGit** | Pure-Java Git implementation. |
| | **Status** | View modified, staged, and untracked files. |
| | **Diff** | Side-by-side file diff viewer. |
| | **AI Sheet Panel** | Git panel integrated into the AI agent sheet. |

### Search & Navigation

| | Feature | Description |
|:---:|:---|:---|
| | **Code Search** | Fast indexed search backed by Room database. |
| | **File Search** | Glob-based file search with exclusion support. |
| | **Find/Replace** | In-file find/replace with replace-all. |
| | **Command Palette** | 30+ commands accessible via keyboard or toolbar. |

### Customization

| | Feature | Description |
|:---:|:---|:---|
| | **Theme Engine** | Material 3 theming with pre-built themes and custom color schemes. |
| | **Font Management** | Separate fonts for editor, terminal, and UI. |
| | **Icon Packs** | Community icon packs via extension system. |
| | **Plugin System** | Extend functionality with community plugins. |
| | **Keybinds** | Fully customizable keyboard shortcuts. |

### Privacy

| | Feature | Description |
|:---:|:---|:---|
| | **No Analytics** | Zero tracking, no Firebase, no analytics SDKs. |
| | **No Telemetry** | No usage data collection. |
| | **Open Source** | GPL v3 -- fully auditable. |

---

## AI Agent Architecture

Xed-Editor supports **four AI agents** through a unified plugin framework. All agents access the editor via the same MCP IDE bridge.

### Supported Agents

| Agent | Binary | Backend | Use Case |
|:---|:---|:---|:---|
| **Gemini CLI** | `gemini-cli-headless` | Google Gemini (flash/pro) | General-purpose AI coding assistant |
| **OpenCode** | `opencode-cli-headless` | opencode CLI | Flexible model configuration |
| **Antigravity CLI** | `antigravity-cli-headless` / `agy.va39` | Go ELF binary via Termux | Autonomous code editing |
| **Codex CLI** | `codex-cli-headless` | OpenAI Codex | AI pair programming |

### Agent Plugin System

All agents live under `core/ai/src/main/java/com/rk/ai/agents/`:

```
AiAgent.kt              -- Interface: name, displayName, cliBinaryName, shellScriptName, buildArgs(), buildEnv()
AgentTypeRegistry.kt    -- Central registry mapping type strings to implementations
AgentProfile.kt         -- Serializable named profiles (name + agentType + extraArgs)
GeminiAgent.kt          -- Gemini CLI implementation
OpenCodeAgent.kt        -- OpenCode implementation
AntigravityAgent.kt     -- Antigravity implementation
CodexAgent.kt           -- Codex CLI implementation
```

Each agent builds its own argument list and environment via `buildArgs()` and `buildEnv()`:
- With a prompt: runs in headless/print mode with the prompt
- Without a prompt: starts an interactive session
- Environment variables like `EDITOR`, `NO_UPDATE_NOTIFIER`, and telemetry controls are set per-agent

### Agent Profiles

Profiles are serialized via `kotlinx.serialization` and stored in `Settings.ai_profiles_json`:

```json
[
  { "name": "Gemini", "agentType": "gemini", "extraArgs": "" },
  { "name": "Codex Pro", "agentType": "codex", "extraArgs": "--model claude-sonnet-4" }
]
```

### Project Configuration

Per-workspace AI config via `.xed/agent.json`:

```json
{
  "agent": "antigravity",
  "extraArgs": ["--model", "flash-2.0"]
}
```

### Agent Launch Scripts

Shell scripts in `core/main/src/main/assets/terminal/` install and launch each agent:
- `antigravity-cli.sh` / `antigravity-cli-headless.sh`
- `codex-cli.sh` / `codex-cli-headless.sh`
- `gemini-cli.sh` / `gemini-cli-headless.sh`
- `opencode-cli.sh` / `opencode-cli-headless.sh`
- `sandbox`, `init`, `setup`, `utils`, `vim`, `code`

---

## MCP IDE Bridge

The IDE bridge is a NanoHTTPD HTTP server that implements the **Model Context Protocol**, giving AI agents direct access to editor functionality.

### Protocol

All agents communicate with the editor via HTTP:

```http
POST /mcp
Content-Type: application/json
Authorization: Bearer <token>

{
  "tool": "readFile",
  "params": { "path": "/path/to/file.kt" }
}
```

### Available Tools

| Category | Tools |
|:---|---|
| **File** | `readFile`, `writeFile`, `createFile`, `deleteFile`, `renameFile` |
| **Editor** | `openFile`, `getOpenFiles`, `getActiveFile`, `replaceSelection`, `insertAtCursor` |
| **LSP** | `getDiagnostics`, `findDefinitions`, `findReferences`, `renameSymbol`, `formatDocument` |
| **Search** | `searchCode`, `searchSymbols`, `grepSearch`, `findFiles` |
| **Git** | `getGitStatus`, `getGitDiff` |
| **System** | `runCommand`, `getTerminalOutput`, `getProjectStructure`, `getProjectSummary` |
| **Web** | `webSearch`, `webFetch` |
| **Package** | `mavenSearch`, `npmSearch`, `pipSearch` |
| **Batch** | `applyBatchEdits` |
| **Cursor** | `getSelection`, `getSymbolUnderCursor` |
| **Diff** | `openDiff`, `rejectDiff`, `getDiffResult` |
| **Terminal** | `getTerminalOutput` |
| **GitHub** | `githubSearchCode`, `githubReadme`, `githubFileFetch`, `githubRepoInfo` |
| **Guidelines** | `getGuidelines` |

### Discovery File Architecture

When the bridge starts, `DiscoveryFileWriter` writes JSON discovery files so agents can auto-connect:

```
/tmp/gemini/ide/gemini-ide-server-<pid>-<port>.json
/tmp/opencode/ide/gemini-ide-server-<pid>-<port>.json
/tmp/antigravity/ide/gemini-ide-server-<pid>-<port>.json
/tmp/codex/ide/gemini-ide-server-<pid>-<port>.json
/tmp/xed-ide/ide-server-<pid>-<port>.json
```

MCP configuration is also written to agent config files:
- `~/.config/opencode/opencode.json` (OpenCode MCP servers)
- `~/.gemini/settings.json` (Gemini MCP servers)

---

## Technical Stack

| Category | Technology | Version |
|:---|---|:---|
| **Language** | Kotlin | 2.3.0 |
| **UI** | Jetpack Compose + Material 3 | BOM 2025.11.01 |
| **Editor** | SoraX (git submodule) | -- |
| **Terminal** | Termux (terminal-emulator, terminal-view) | -- |
| **LSP** | Eclipse LSP4J | 1.0.0 |
| **Regex** | Tree-sitter, Joni, Oniguruma, RE2/J | -- |
| **Networking** | OkHttp 5.3.2, NanoHTTPD 2.3.1 | -- |
| **Serialization** | kotlinx.serialization-json | 1.10.0 |
| **Git** | JGit | 6.2.0 |
| **Database** | Room | 2.8.4 |
| **Images** | Glide 5.0.5, Coil 2.7.0 | -- |
| **JavaScript** | QuickJS (app.cash.quickjs) | 0.9.2 |
| **Build** | AGP 8.13.1, Gradle, KSP, ktfmt | -- |
| **CI** | GitHub Actions | -- |

---

## Module Architecture

The project is a multi-module Gradle project with 15+ modules:

### Application

```
:app                    -- Thin APK shell, depends on :core:main, :core:ai, and :core:vibe-coding:ai-integration
:baselineprofile        -- Android Baseline Profile generation
:benchmark / :benchmark2 -- Macrobenchmarks
```

### Core Libraries

```
:core:main              -- Main library: editor, UI, LSP, terminal, git, search, settings, themes, extensions
:core:ai                -- External AI agent system: agents, MCP bridge, IDE tools, session management
:core:components        -- Shared Compose UI components (appbars, preferences, icons)
:core:resources         -- Centralized resource access (Res.kt)
:core:extension         -- Plugin/extension system API
:core:terminal-emulator -- Termux terminal emulator engine (Java + C/JNI)
:core:terminal-view     -- Termux terminal rendering (TerminalView, TerminalRenderer)
:core:termux-shared     -- Shared Termux utilities (shell commands, extra keys, preferences)

### Vibe-Coding / AI Core (under :core:vibe-coding:)

```
:core:vibe-coding:ai-integration -- Native in-process AI agent: engine, tools, Compose UI
:core:vibe-coding:ai-core        -- Base types: MessageRole, TokenUsage, caching
:core:vibe-coding:ai-streaming   -- SSE streaming, KeyRoulette, error parsing
:core:vibe-coding:ai-models      -- Data models: Message, Conversation, Tool, Image
:core:vibe-coding:ai-providers   -- LLM providers: OpenAI, Google, Claude
:core:vibe-coding:ai-mcp-client  -- MCP client (SSE + Streamable HTTP)
:core:vibe-coding:ai-persistence -- Room DB + DataStore persistence
:core:vibe-coding:agent-runtime  -- AI orchestration engine: GenerationHandler, transformers
:core:vibe-coding:agent-tools-search -- 15+ web search providers
```
```

### SoraX Editor Engine (Git Submodules)

```
:editor                 -- Sora Editor core widget
:editor-lsp             -- LSP integration for Sora Editor
:language-textmate      -- TextMate grammar support
:oniguruma-native       -- Oniguruma regex native bindings
```

### Plugin SDK (Separate Project)

```
plugin-sdk/             -- Standalone JVM Gradle project that produces sdk.jar for extensions
```

### Key Source Packages

| Package | Module | Purpose |
|:---|---|:---|
| `com.rk.activities.main` | `:core:main` | Main activity, tab/session/editor management |
| `com.rk.editor` | `:core:main` | Editor integration, syntax highlighting, themes |
| `com.rk.lsp` | `:core:main` | LSP connection manager + built-in servers |
| `com.rk.terminal` | `:core:main` | Terminal integration, shell scripts, sessions |
| `com.rk.git` | `:core:main` | Git operations, status, diff |
| `com.rk.search` | `:core:main` | Code/file search with Room indexing |
| `com.rk.file` | `:core:main` | File management, operations, types |
| `com.rk.settings` | `:core:main` | Full settings hierarchy |
| `com.rk.theme` | `:core:main` | Theme engine, Material 3 theming |
| `com.rk.ai` | `:core:ai` | AI agents, MCP bridge, sessions, profiles |
| `com.rk.ai.bridge.server` | `:core:ai` | NanoHTTPD MCP server |
| `com.rk.ai.bridge.tools` | `:core:ai` | 21+ MCP tool implementations |
| `com.termux.terminal` | `:core:terminal-emulator` | Terminal emulator engine |
| `com.termux.view` | `:core:terminal-view` | Terminal rendering |

---

## Getting Started

### Download

Download the latest APK from:
- [GitHub Releases](https://github.com/algospider/Xed-Editor/releases)
- [GitHub Actions](https://github.com/algospider/Xed-Editor/actions) (nightly debug builds)

### Quick Start

1. Install the APK on your Android device (Android 8.0+)
2. Open the AI Sheet from the toolbar
3. Configure your API key for the desired agent (Gemini / OpenCode / Antigravity / Codex)
4. Start coding with AI assistance

---

## Build Instructions

### Prerequisites

- JDK 21 (Temurin recommended)
- Android Studio (latest stable)
- Android SDK 36
- 4GB+ RAM

### Setup

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/algospider/Xed-Editor.git
cd Xed-Editor

# Ensure SoraX and its nested submodules are present before running Gradle
git submodule update --init --recursive
# CI can use the fallback helper if a shallow checkout leaves soraX empty
bash .github/scripts/ensure-sorax.sh
```

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (requires signing config)
./gradlew assembleRelease

# Format code
./gradlew ktfmtFormat

# Run checks
./gradlew lint
```

### Signing

- Debug builds use `testkey.keystore` (password: `testkey`) from the project root
- Release builds expect signing properties at:
  - Local: `/home/rohit/Android/xed-signing/signing.properties`
  - CI: `/tmp/signing.properties`

### CI

GitHub Actions workflows are in `.github/workflows/`:
- `android.yml` -- Full release pipeline (build, sign, archive, create GitHub Release)
- `debug-build.yml` -- Debug APK build
- `ktfmt-check.yml` -- Code formatting validation
- `deleteoldrus.yml` -- Cleanup old CI runs
- `duplicate-issue.yml` -- Automated duplicate issue handling

---

## Plugin SDK

The `plugin-sdk/` directory is a standalone Gradle project for building Xed-Editor extensions:

```bash
cd plugin-sdk
./gradlew build
```

Output: `./output/sdk.jar`

The SDK enables developers to create:
- Custom icon packs
- Editor extensions
- Integration plugins

---

## Contributing

See [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for full guidelines.

### Code Style

- ktfmt with Kotlin style, 120 max width
- Pre-commit hook at `.githooks/`
- Run `./gradlew ktfmtFormat` before committing

### Development Tips

- Enable Gradle configuration cache for faster builds
- Use Android Studio with Kotlin plugin 2.3.0+
- Test on Android 8.0+ (API 26)

### Contributors

<a href="https://github.com/algospider/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=algospider/Xed-Editor" />
</a>

---

## License

Copyright (C) 2025 Rohit Kushvaha

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

<div align="center">
  <sub>Made with by the Xed-Editor Community</sub>
</div>
