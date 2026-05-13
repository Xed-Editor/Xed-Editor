# 🚀 Xed-Editor

<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="120" height="120" />
  <h3>The AI-Powered Android Code Editor</h3>
  <p>
    <b>Versatile. Extensible. Intelligent.</b>
  </p>

  ![Android CI](https://github.com/algospider/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge)
  ![Download count](https://img.shields.io/github/downloads/algospider/Xed-Editor/total?label=Downloads&style=for-the-badge)
  [![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/6bKzcQRuef)
  [![Telegram](https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/XedEditor)
</div>

---

**Xed-Editor** is a high-performance, extensible text editor for Android. This fork pushes the boundaries of mobile development by deeply integrating **AI agents** (Gemini & OpenCode) via a robust **MCP (Model Context Protocol) IDE bridge**, granting them autonomous code editing capabilities directly on your device.

## 📖 Table of Contents
- [✨ Key Features](#-key-features)
- [🤖 AI Agent Integration](#-ai-agent-integration)
- [🛠 Technical Stack](#-technical-stack)
- [📸 Screenshots](#-screenshots)
- [📥 Getting Started](#-getting-started)
- [🤝 Contributing](#-contributing)
- [📄 License](#-license)

---

## ✨ Key Features

- 🧠 **Dual AI Agent Support**: Seamlessly toggle between **Gemini CLI** and **OpenCode** agents.
- 🔌 **Full MCP IDE Bridge**: 30+ tools allowing agents to read/write files, run commands, and navigate code.
- 🔍 **Diff Review**: Safety first—preview and approve all AI-suggested changes side-by-side.
- ⚡ **LSP Intelligence**: Full code completion, diagnostics, and symbol navigation.
- 🖥️ **Built-in Terminal**: Integrated Termux-based emulator with proot/Ubuntu support.
- 📝 **Advanced Editor**: Powered by Sora Editor for smooth handling of massive files.
- 🧩 **Extension System**: Customize with community plugins and icon packs.
- 🎨 **Modern UI**: Clean, Material 3 design built with Jetpack Compose.

---

## 🤖 AI Agent Integration

Xed-Editor isn't just an editor; it's an AI-native workspace.

### 🛠 The MCP IDE Bridge
Agents interact with the editor via a high-performance NanoHTTPD server implementing the Model Context Protocol.

| Category | Capabilities |
| :--- | :--- |
| **Files** | `readFile`, `writeFile`, `createFile`, `deleteFile`, `renameFile` |
| **Editor** | `openFile`, `getOpenFiles`, `replaceSelection`, `insertAtCursor` |
| **Intelligence** | `getDiagnostics`, `findDefinitions`, `findReferences`, `renameSymbol` |
| **Git** | `getGitStatus`, `getGitDiff` |
| **System** | `runCommand`, `getTerminalOutput`, `getProjectStructure` |

### 🤖 Supported Agents
- **Gemini CLI**: Powered by Google's latest models (`flash` or `pro`).
- **OpenCode**: Flexible agent support for custom model configurations.

---

## 🛠 Technical Stack

Built with modern Android standards for maximum performance and reliability.

- **Language**: 100% Kotlin
- **UI**: Jetpack Compose + Material 3
- **Editor**: Sora Editor
- **Terminal**: Termux Runtime
- **Networking**: OkHttp + NanoHTTPD (MCP)
- **Concurrency**: Kotlin Coroutines & Flow

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

1. **Download**: Grab the latest APK from [Releases](https://github.com/algospider/Xed-Editor/releases) or [GitHub Actions](https://github.com/algospider/Xed-Editor/actions).
2. **Setup AI**:
   - Open the AI Sheet.
   - Configure your API key (Gemini/OpenCode).
   - Start chatting and let the agent help you code!
3. **Docs**: Check out the [Official Documentation](https://xed-editor.github.io/Xed-Docs/) for advanced usage.

---

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](/docs/CONTRIBUTING.md) to get started.

### Contributors
<a href="https://github.com/algospider/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=algospider/Xed-Editor" />
</a>

---

## 📄 License

Xed-Editor is licensed under the [MIT License](/LICENSE).
