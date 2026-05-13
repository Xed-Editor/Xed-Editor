<p align="center">
  <img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="128" height="128" />
</p>

<h1 align="center">🚀 Xed-Editor</h1>
<h3 align="center">The AI-Powered Android Code Editor</h3>

<p align="center">
  <b>Versatile. Extensible. Intelligent.</b>
</p>

<p align="center">
  <a href="https://github.com/algospider/Xed-Editor/actions/workflows/android.yml">
    <img src="https://github.com/algospider/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge" alt="Android CI" />
  </a>
  <a href="https://github.com/algospider/Xed-Editor/releases">
    <img src="https://img.shields.io/github/downloads/algospider/Xed-Editor/total?label=Downloads&style=for-the-badge&color=blue" alt="Download count" />
  </a>
  <a href="https://discord.gg/6bKzcQRuef">
    <img src="https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white" alt="Discord" />
  </a>
  <a href="https://t.me/XedEditor">
    <img src="https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white" alt="Telegram" />
  </a>
  <a href="https://github.com/algospider/Xed-Editor/blob/main/LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License" />
  </a>
</p>

<br />

> **Xed-Editor** is a high-performance, extensible text editor for Android. This fork pushes the boundaries of mobile development by deeply integrating **AI agents** (Gemini & OpenCode) via a robust **MCP (Model Context Protocol) IDE bridge**, granting them autonomous code editing capabilities directly on your device.

<br />

---

## 📋 Table of Contents

| Jump to | |
|:---|---:|
| [✨ Key Features](#-key-features) | [🤖 AI Agent Integration](#-ai-agent-integration) |
| [🛠 Technical Stack](#-technical-stack) | [📸 Screenshots](#-screenshots) |
| [📥 Getting Started](#-getting-started) | [🤝 Contributing](#-contributing) |
| [📄 License](#-license) | |

---

## ✨ Key Features

<div align="center">

| | Feature | Description |
|:---:|:---|:---|
| 🧠 | **Dual AI Agent Support** | Seamlessly toggle between **Gemini CLI** and **OpenCode** agents. |
| 🔌 | **Full MCP IDE Bridge** | 30+ tools for agents to read/write files, run commands, and navigate code. |
| 🔍 | **Diff Review** | Safety first—preview and approve all AI-suggested changes side-by-side. |
| ⚡ | **LSP Intelligence** | Full code completion, diagnostics, and symbol navigation. |
| 🖥️ | **Built-in Terminal** | Integrated Termux-based emulator with proot/Ubuntu support. |
| 📝 | **Advanced Editor** | Powered by Sora Editor for smooth handling of massive files. |
| 🧩 | **Extension System** | Customize with community plugins and icon packs. |
| 🎨 | **Modern UI** | Clean, Material 3 design built with Jetpack Compose. |

</div>

---

## 🤖 AI Agent Integration

Xed-Editor isn't just an editor — it's an **AI-native workspace**.

### 🛠 The MCP IDE Bridge

Agents interact with the editor via a high-performance NanoHTTPD server implementing the **Model Context Protocol**.

| Category | Capabilities |
|:---|---:|
| 📁 **Files** | `readFile` · `writeFile` · `createFile` · `deleteFile` · `renameFile` |
| ✏️ **Editor** | `openFile` · `getOpenFiles` · `replaceSelection` · `insertAtCursor` |
| 🔬 **Intelligence** | `getDiagnostics` · `findDefinitions` · `findReferences` · `renameSymbol` |
| 🔄 **Git** | `getGitStatus` · `getGitDiff` |
| ⚙️ **System** | `runCommand` · `getTerminalOutput` · `getProjectStructure` |

### 🤖 Supported Agents

| Agent | Backend |
|:---|:---|
| **Gemini CLI** | Powered by Google's latest models (`flash` or `pro`) |
| **OpenCode** | Flexible agent support for custom model configurations |

---

## 🛠 Technical Stack

<div align="center">

| | Technology | |
|:---|---:|:---|
| 🧑‍💻 | **Language** | 100% Kotlin |
| 🎨 | **UI** | Jetpack Compose + Material 3 |
| 📄 | **Editor** | Sora Editor |
| 💻 | **Terminal** | Termux Runtime |
| 🌐 | **Networking** | OkHttp + NanoHTTPD (MCP) |
| ⚡ | **Concurrency** | Kotlin Coroutines & Flow |

</div>

---

## 📸 Screenshots

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

## 📥 Getting Started

| Step | Action |
|:---:|:---|
| **1** | 📲 Download the latest APK from [Releases](https://github.com/algospider/Xed-Editor/releases) or [GitHub Actions](https://github.com/algospider/Xed-Editor/actions). |
| **2** | 🤖 Open the **AI Sheet**, configure your API key (Gemini/OpenCode), and start coding with AI! |
| **3** | 📖 Check out the [Official Documentation](https://xed-editor.github.io/Xed-Docs/) for advanced usage. |

---

## 🤝 Contributing

We welcome contributions! See our [Contributing Guide](/docs/CONTRIBUTING.md) to get started.

### 🌟 Contributors

<a href="https://github.com/algospider/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=algospider/Xed-Editor" />
</a>

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](/LICENSE) file for details.

<p align="center">
  <sub>Made with ❤️ by the Xed-Editor Community</sub>
</p>
