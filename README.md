<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="128" height="128" />
</div>

<h1 align="center">Xed-Editor</h1>

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

---

An Android code editor with AI built in. Fork of the original by Rohit Kushvaha, maintained by [algospider](https://github.com/algospider).

## What is this?

Xed is a code editor for Android that runs a full terminal (Termux-based), has LSP support for a bunch of languages, and integrates multiple AI agents so you can edit code through chat. It's kind of like having Cursor or Windsurf on your phone, except it's open source and doesn't phone home.

The built-in "vibe coding" agent runs on-device — reads your project, makes edits, runs commands, manages git. All through a multi-agent pipeline that keeps track of context, loops, and tool calls.

## Features that actually matter

- **AI agents** — built-in native agent + support for Gemini CLI, OpenCode, Codex CLI, Antigravity. Pick your poison.
- **Terminal** — proper Termux terminal with proot/Ubuntu support, session management, extra keys
- **Code intelligence** — LSP for Python, TS/JS, HTML, CSS, JSON, XML, Markdown, Bash. Diagnostics, completions, go-to-def, hover docs
- **Editor** — SoraX engine with syntax highlighting, minimap, multi-cursor, code folding, bracket matching
- **Search** — index-based code search via Room DB, find/replace in files, command palette
- **Customizable** — Material 3 theming, custom fonts for editor/terminal/UI, keybinds, icon packs, plugins
- **No tracking** — zero telemetry, no Firebase, no analytics. Your code doesn't leave your device unless you want it to

## Screenshots

<div align="center">
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/01.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/02.jpg" width="30%" />
  <img src="/fastlane/metadata/android/en-US/images/phoneScreenshots/03.jpg" width="30%" />
</div>

## Getting started

1. Grab the [latest APK](https://github.com/algospider/Xed-Editor/releases) (Android 8.0+)
2. Open the AI sheet from the toolbar
3. Drop in an API key for whatever agent you want to use
4. That's it — start typing what you want done

Nightly debug builds are on [GitHub Actions](https://github.com/algospider/Xed-Editor/actions) if you're feeling adventurous.

## Release workflow

Releases are triggered manually from GitHub Actions (`Android Release CI` workflow):

1. Go to **Actions → Android Release CI → Run workflow**
2. Enter the version name (or leave blank to use `version.properties`)
3. Optionally enter changelog notes, or let it pull from `CHANGELOG.md`
4. The workflow builds the signed APK, creates a GitHub release, and auto-bumps `versionCode`

For local version bumps, use `./scripts/bump-version.sh`:
```
./scripts/bump-version.sh patch     # 3.2.9 → 3.2.10
./scripts/bump-version.sh minor     # 3.2.9 → 3.3.0
./scripts/bump-version.sh major     # 3.2.9 → 4.0.0
./scripts/bump-version.sh manual 3.5.0  # set specific version
```

Add release notes to `CHANGELOG.md` under the new version header before running the release.

## How the AI actually works

The native vibe-coding agent uses a pipeline architecture:

- **GenerationHandler** manages multi-step LLM interactions — calls the model, handles tool call loops, compaction, doom-loop detection
- **Transformer chain** — input/output transformers that handle placeholders, prompt injection, think tags, base64 images, lorebook documents
- **Tool system** — 30+ tools the agent can call (read/write files, search, run commands, git, LSP queries, web fetch, etc.)
- **Security hooks** — blocks dangerous patterns (eval, pickle, SQL injection) before writes
- **Context memory** — keeps track of project structure, recent edits, tool history across the session

Under the hood it's talking to OpenAI-compatible APIs, Google AI, Claude, or whatever provider you configure. The MCP bridge lets external agents hook into the same editor tools.

## Why another editor?

Because nothing on Android had all of these things together. Existing editors either had no AI, no terminal, no LSP, or were abandonware. This one is actively maintained and actually works on a phone screen.

## Community

- [Discord](https://discord.gg/6bKzcQRuef) — where most of the chat happens
- [Telegram](https://t.me/XedEditor) — announcements
- [GitHub Issues](https://github.com/algospider/Xed-Editor/issues) — bugs, feature requests, rants

## Credits

This is a fork of Rohit Kushvaha's original work, maintained by [algospider](https://github.com/algospider) (Mohan Sharma). The vibe-coding agent uses [RikkaHub](https://github.com/RikkaHub) under the hood.

Thanks to everyone who's submitted PRs, filed bug reports, or just used the thing.

## License

GPL v3. See [LICENSE](LICENSE).

Copyright (C) 2025 Rohit Kushvaha — Fork maintained by algospider (Mohan Sharma)

<div align="center">
  <sub>Made with ❤️ by the Xed-Editor Community</sub>
</div>
