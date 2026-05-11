## Xed-Editor (Gemini Edition)

<img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="90" height="90" align="left"/>

**Xed-Editor** is a versatile and extensible text editor for Android, featuring syntax highlighting,
LSP-powered code intelligence, a built-in terminal, extensions, and fast project-wide tools for
efficient editing. This fork features a **deeply integrated Gemini AI assistant** with autonomous
IDE capabilities.

![Android CI](https://github.com/algospider/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge)
![Download count](https://img.shields.io/github/downloads/algospider/Xed-Editor/total?label=Downloads)

---

## 🚀 Features

- **Deep AI Integration**: Integrated Gemini AI assistant with full IDE bridge capabilities (MCP). Gemini can read/write files, manage projects, and run terminal commands autonomously.
- **Advanced Editing**: Powered by [Sora Editor](https://github.com/Rosemoe/sora-editor), supporting large files and smooth performance.
- **Code Intelligence**: Full LSP (Language Server Protocol) support for code completion, diagnostics, and navigation.
- **Built-in Terminal**: Integrated terminal emulator based on Termux, allowing you to run build commands and scripts directly.
- **Extension System**: Customize your experience with community-driven extensions and icon packs.
- **Modern UI**: Fully built with Jetpack Compose, featuring a clean, responsive Material 3 design.
- **Project Management**: Efficient file drawer with multi-file selection, Git status integration, and fast search.
- **Enhanced Productivity**: Integrated color picker, minimap support, and fullscreen mode.
- **Customization**: Extensive settings for themes, fonts, and editor behavior.

---

## 🤖 Gemini AI Integration

This fork introduces a powerful co-developer experience powered by Google's Gemini.

- **Unified Gemini Sheet**: A persistent, minimize-able interface for AI interactions.
- **IDE Bridge (MCP)**: Gemini can autonomously:
    - **Read/Write Files**: Update code with precision.
    - **Diff View**: Preview and approve changes before they are applied.
    - **Terminal Access**: Run commands and scripts.
    - **Project Context**: Automatically understands your workspace structure.
- **Session Persistence**: Resume your AI conversations across different files and tasks.

---

## 🛠 Technical Stack

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose
- **Editor Engine**: [Sora Editor](https://github.com/Rosemoe/sora-editor)
- **Terminal Emulator**: Integrated components from [Termux](https://github.com/termux/termux-app)
- **AI Bridge**: NanoHTTPD (MCP Server) + Gemini CLI
- **Build System**: Gradle (Kotlin DSL)
- **Concurrency**: Kotlin Coroutines & Flow

---

## 📂 Project Structure

- `app/`: The main Android application module.
- `core/`: Core logic and components.
    - `main/`: Core application logic, ViewModels, and UI components (includes AI integration).
    - `terminal-emulator/`: Termux-based terminal logic.
    - `terminal-view/`: UI components for the terminal.
    - `extension/`: Extension system API and management.
- `plugin-sdk/`: SDK for developing Xed-Editor extensions.
- `benchmark/`: Performance benchmarks.

---

## 📚 Documentation

To learn more about Xed-Editor‘s features and usage, visit the official
documentation: [https://xed-editor.github.io/Xed-Docs/](https://xed-editor.github.io/Xed-Docs/)

[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/6bKzcQRuef)
[![Telegram](https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/XedEditor)

---

## 📦 Download

- **Latest Builds**: Download from [GitHub Actions](https://github.com/algospider/Xed-Editor/actions/)
- **Releases**: Download from [GitHub Releases](https://github.com/algospider/Xed-Editor/releases)

---

## 📸 Screenshots

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

## 🤝 Contributing

We welcome contributions! Please read the [`/docs/CONTRIBUTING.md`](/docs/CONTRIBUTING.md) file to learn how you can get involved.

---

## ❤️ Contributors

<a href="https://github.com/algospider/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=algospider/Xed-Editor" />
</a>
