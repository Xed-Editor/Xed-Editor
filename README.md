<p align="center">
  <img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Logo" width="120" height="120"/>
</p>

<h1 align="center">Xed-Editor</h1>

<p align="center">
  <strong>A powerful, open-source code editor for Android</strong>
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/github/license/Xed-Editor/Xed-Editor?color=blue" alt="License"></a>
  <a href="https://github.com/Xed-Editor/Xed-Editor/releases/latest"><img src="https://img.shields.io/github/v/release/Xed-Editor/Xed-Editor" alt="Release"></a>
  <a href="https://github.com/Xed-Editor/Xed-Editor/releases"><img src="https://img.shields.io/github/downloads/Xed-Editor/Xed-Editor/total" alt="Downloads"></a>
  <a href="https://github.com/Xed-Editor/Xed-Editor/actions"><img src="https://github.com/Xed-Editor/Xed-Editor/actions/workflows/android.yml/badge.svg" alt="CI"></a>
  <br/>
  <img src="https://img.shields.io/badge/Android-8.0%2B-green?logo=android" alt="Min Android">
  <img src="https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin" alt="Kotlin">
  <a href="https://f-droid.org/packages/com.rk.xededitor"><img src="https://img.shields.io/f-droid/v/com.rk.xededitor" alt="F-Droid"></a>
</p>

---

**Xed-Editor** brings desktop-grade code editing capabilities to your Android device. With syntax highlighting for 20+ languages, Language Server Protocol (LSP) support for intelligent code completion, and an integrated terminal emulator, Xed-Editor is the perfect companion for developers on the go.

**Why Xed-Editor?**

- **Full-featured** - LSP support, syntax highlighting, terminal - all in one app
- **Privacy-focused** - No ads, no tracking, no cloud dependency
- **Customizable** - Themes, fonts, keyboard shortcuts - make it yours
- **Open Source** - GPL-3.0 licensed, community-driven development

---

## Table of Contents

- [Features](#features)
- [Screenshots](#screenshots)
- [Installation](#installation)
  - [Download](#download)
  - [Build from Source](#build-from-source)
- [Requirements](#requirements)
- [Tech Stack](#tech-stack)
- [Contributing](#contributing)
- [Community](#community)
- [Translations](#translations)
- [Acknowledgments](#acknowledgments)
- [License](#license)
- [Contributors](#contributors)

---

## Features

- **Syntax Highlighting** - Support for 20+ programming languages using TextMate grammars
- **Language Server Protocol (LSP)** - Intelligent code completion, error highlighting, and inline documentation
- **Integrated Terminal** - Built-in terminal emulator with virtual keyboard support
- **Extensive Customization** - Customize themes, fonts, text size, and editor behavior
- **Plugin Support** - Extend functionality with third-party extensions
- **Modern UI** - Material Design 3 interface with light/dark modes and AMOLED support

---

## Screenshots

<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" width="30%" alt="Editor View"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="30%" alt="File Browser"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="30%" alt="Terminal"/>
</div>

<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/04.jpg" width="30%" alt="Settings"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/05.jpg" width="30%" alt="Theme Options"/>
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/06.jpg" width="30%" alt="LSP Features"/>
</div>

---

## Installation

### Download

Get Xed-Editor from your preferred source:

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.rk.xededitor)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.rk.xededitor)
[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/Xed-Editor/Xed-Editor/releases/latest)

| Channel | Description |
|---------|-------------|
| [F-Droid](https://f-droid.org/packages/com.rk.xededitor) | Stable releases, built by F-Droid |
| [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.rk.xededitor) | Stable releases, faster updates |
| [GitHub Releases](https://github.com/Xed-Editor/Xed-Editor/releases/latest) | Stable releases, direct download |
| [GitHub Actions](https://github.com/Xed-Editor/Xed-Editor/actions) | Alpha/development builds |

### Build from Source

```bash
# Clone the repository
git clone https://github.com/Xed-Editor/Xed-Editor.git
cd Xed-Editor

# Build debug APK
./gradlew assembleDebug

# Or install directly on connected device
./gradlew installDebug
```

For detailed contribution guidelines, see [CONTRIBUTING.md](docs/CONTRIBUTING.md).

---

## Requirements

| Requirement | Details |
|-------------|---------|
| **Android Version** | 8.0 (Oreo) or higher (API level 26+) |
| **Storage** | ~50 MB for the app |
| **Optional** | [Termux](https://termux.dev/) for terminal and LSP features |

---

## Tech Stack

Xed-Editor is built with modern Android technologies:

| Technology | Purpose |
|------------|---------|
| [Kotlin](https://kotlinlang.org/) | Primary language |
| [Jetpack Compose](https://developer.android.com/jetpack/compose) | UI framework |
| [Material Design 3](https://m3.material.io/) | Design system |
| [Sora Editor](https://github.com/Rosemoe/sora-editor) | Code editor widget |
| [TextMate Grammars](https://macromates.com/manual/en/language_grammars) | Syntax highlighting |
| [LSP4J](https://github.com/eclipse-lsp4j/lsp4j) | Language Server Protocol |

---

## Contributing

We welcome contributions of all kinds! Whether you're fixing bugs, adding features, improving documentation, or translating - every contribution makes Xed-Editor better.

**Quick Start:**

```bash
# Fork and clone the repo
git clone https://github.com/YOUR_USERNAME/Xed-Editor.git

# Create a feature branch
git checkout -b feature/my-feature

# Make changes and format code
./gradlew ktfmtFormat

# Commit and push
git commit -m "Add my feature"
git push origin feature/my-feature
```

See [CONTRIBUTING.md](docs/CONTRIBUTING.md) for detailed guidelines.

---

## Community

Join our community to get help, share ideas, and stay updated:

- [Telegram](https://t.me/XedEditor) - Chat and announcements
- [Discord](https://discord.gg/6bKzcQRuef) - Discussion and development

---

## Translations

Help make Xed-Editor accessible to everyone! Translate on [Weblate](https://hosted.weblate.org/engage/xed-editor/):

<a href="https://hosted.weblate.org/engage/xed-editor/">
  <img src="https://hosted.weblate.org/widgets/xed-editor/-/multi-auto.svg" alt="Translation Status">
</a>

---

## Acknowledgments

Xed-Editor wouldn't be possible without these amazing open-source projects:

- [Sora Editor](https://github.com/Rosemoe/sora-editor) - The powerful code editor widget at the heart of Xed
- [Termux](https://github.com/termux/termux-app) - Terminal emulator components
- [TextMate](https://macromates.com/) - Syntax highlighting grammars
- [LSP4J](https://github.com/eclipse-lsp4j/lsp4j) - Language Server Protocol implementation

Special thanks to all [contributors](#contributors) who have helped make this project better!

---

## License

Xed-Editor is open source software licensed under the [GNU General Public License v3.0](LICENSE).

```
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

## Contributors

<a href="https://github.com/Xed-Editor/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Xed-Editor/Xed-Editor" alt="Contributors"/>
</a>

---

<p align="center">
  Made with ❤️ by the Xed-Editor community
</p>
