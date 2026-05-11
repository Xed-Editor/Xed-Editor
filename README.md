## Xed-Editor

<img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="90" height="90" align="left"/>

**Xed-Editor** is a versatile and extensible text editor for Android, featuring syntax highlighting,
LSP-powered code intelligence, a built-in terminal, extensions, and fast project-wide tools for
efficient editing.

![Android CI](https://github.com/Rohitkushvaha01/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge)
![Download count](https://img.shields.io/github/downloads/Xed-Editor/Xed-Editor/total?label=Downloads)

---

## 🚀 Features

- **Advanced Editing**: Powered by [Sora Editor](https://github.com/Rosemoe/sora-editor), supporting large files and smooth performance.
- **Code Intelligence**: Full LSP (Language Server Protocol) support for code completion, diagnostics, and navigation.
- **Built-in Terminal**: Integrated terminal emulator based on Termux, allowing you to run build commands and scripts directly.
- **Extension System**: Customize your experience with community-driven extensions and icon packs.
- **Modern UI**: Fully built with Jetpack Compose, featuring a clean, responsive Material 3 design.
- **AI Integration**: Built-in Gemini AI assistant to help with coding tasks, debugging, and terminal commands.
- **Project Management**: Efficient file drawer with multi-file selection, Git status integration, and fast search.
- **Enhanced Productivity**: Integrated color picker, minimap support, and fullscreen mode for a more immersive experience.
- **Customization**: Extensive settings for themes, fonts (app, editor, terminal), and editor behavior (word wrap, auto-save, etc.).

---

## 🛠 Technical Stack

- **Language**: 100% Kotlin
- **UI Framework**: Jetpack Compose
- **Editor Engine**: [Sora Editor](https://github.com/Rosemoe/sora-editor)
- **Terminal Emulator**: Integrated components from [Termux](https://github.com/termux/termux-app)
- **Build System**: Gradle (Kotlin DSL)
- **Serialization**: Kotlin Serialization
- **Concurrency**: Kotlin Coroutines & Flow

---

## 📂 Project Structure

- `app/`: The main Android application module.
- `core/`: Core logic and components.
    - `main/`: Core application logic, ViewModels, and UI components.
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

<div>
  <a href="https://android.izzysoft.de/repo/apk/com.rk.xededitor">
    <img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.rk.xededitor&label=IzzyOnDroid&cacheSeconds=33000">
  </a>
  <a href="https://f-droid.org/packages/com.rk.xededitor">
    <img src="https://img.shields.io/f-droid/v/com.rk.xededitor">
  </a>
</div>

- **Latest Alpha Build**: Download from [Actions](https://github.com/Xed-Editor/Xed-Editor/actions/)
- **Latest Stable Build**: Download from [Releases](https://github.com/Xed-Editor/Xed-Editor/releases)

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.rk.xededitor)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png?ref_type=heads" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.rk.xededitor)
[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/Xed-Editor/Xed-Editor/releases/latest)

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

## 🌍 Translations

Help translate Xed-Editor! Visit [Weblate](https://hosted.weblate.org/engage/xed-editor/) to get started:

<a href="https://hosted.weblate.org/engage/xed-editor/">
    <img src="https://hosted.weblate.org/widgets/xed-editor/-/multi-auto.svg" alt="Translation Status">
</a>

---

## ❤️ Contributors

<a href="https://github.com/Xed-Editor/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Xed-Editor/Xed-Editor" />
</a>
