# Changelog — Xed-Editor PRO

**Version 3.3.4 (versionCode 98)** — bumped +0.0.1 from upstream 3.3.3.

This document is the complete list of everything added/changed in this fork relative to upstream
Xed-Editor, from the very beginning of the PRO work.

## Animations
- Material-standard easing for screen/navigation transitions (incoming decelerates, outgoing eases),
  snappier 300 ms duration, and stable list keys.
- Animated (Crossfade) state transitions in the native Markdown preview.
- Animated Git change sections; Material dialogs/sheets animate by default.

## Project creation
- **New "Create Project" entry** in the project switcher's `+` sheet (`AddProjectSheet`), hosted in `Drawer`.
- **`CreateProjectDialog`** (Material 3) with: project name (validated), template picker, per-template options, a live resolved-location preview, an **Initialize Git repository** toggle, and a **live toolchain status** line.
- **Templates** (`ProjectTemplate`, `ProjectScaffolder`) — real, distinct scaffolds:
  - **None** – README only.
  - **Python 3 / Python** – `main.py`, `requirements.txt`, `.gitignore`, README.
  - **Node.js** – `package.json`, `index.js`, `.gitignore`, README.
  - **Static Web (HTML/CSS/JS)** – `index.html`, `style.css`, `script.js` (pairs with the HTML runner).
  - **Minecraft Java Mod** – Fabric *or* Forge, with **real Minecraft versions fetched from Mojang's manifest** (`MinecraftVersions`), and full scaffolds:
    - Fabric: `fabric.mod.json`, `build.gradle` (Loom), `gradle.properties`, `settings.gradle`, mixins config, `ModInitializer` main class.
    - Forge: `mods.toml`, `build.gradle` (ForgeGradle), `pack.mcmeta`, `@Mod` main class.
  - **Android (Jetpack Compose)** – a **complete Android Studio "Empty Activity" project**: Gradle **version catalog** (`gradle/libs.versions.toml`), root + app `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `AndroidManifest.xml` (backup + data-extraction rules), `MainActivity.kt` with `@Preview`, a full **theme package** (`ui/theme/Color.kt`, `Theme.kt` with dynamic color, `Type.kt`), **adaptive launcher icons** (vector background/foreground + `mipmap-anydpi-v26` + legacy fallback), `proguard-rules.pro`, `colors.xml`/`strings.xml`/`themes.xml` (+ `values-night`), and **unit + instrumented test** sources.
- **Intelligent derivation**: package name / applicationId and mod id are derived from the **project name + author** (`com.<author>.<name>`) when not supplied (`ProjectConfig.resolvedPackageName()`, `resolvedModId()`).
- **Working Gradle wrapper**: `gradlew`, `gradlew.bat`, and `gradle-wrapper.jar` are bundled as app assets and copied into generated Gradle projects (plus `gradle-wrapper.properties`), so `./gradlew` works and the old `chmod: cannot access 'gradlew'` error is gone.
- **Storage location**: every project is created inside the **terminal sandbox home** (`home/<name>`),
  which is exec-capable, so its toolchain (gradle/npm/python) can actually run from where the project
  lives. The old Documents/XED location and the per-project "build in sandbox" toggle were removed —
  Android shared storage is mounted noexec and ignores Unix permissions, so nothing buildable could
  run there anyway.
- **Cloning a repo** no longer asks where to save: it clones straight into the sandbox home, each repo
  in its own folder (`home/goyapp`, `home/blah`, …) — unique-suffixed if the name already exists so
  multiple clones never collide.

## Dependencies
- **Automatic dependency detection/install** after project creation (`ProjectDependencies`): detects missing tools (Python/Node/JDK) via the sandbox and offers a one-tap install.
- **Live tool status** in the create dialog (checking / installed / will-install / set-up-terminal).
- **Dependencies download dialog** (`DependenciesDialog`), opened from a new **download button** in the editor toolbar:
  - Detects the project type and lists required tools with **Installed / Available** status (JDK 17/21, Git, Node, Python/pipx, Rust, Go, and the **Android SDK command-line tools** for Android projects).
  - Installs selected tools in the **background via a foreground service** (`DependencyInstallService`, runs arbitrary install commands — apt packages or the Android SDK installer) with a **progress notification**, so installs keep running even if you leave the app; the dialog observes live per-tool progress and can't be dismissed mid-install.
  - Requests **notification permission** (Android 13+) before starting.

## Project type detection
- **`ProjectTypeDetector`** infers the project kind (Fabric/Forge mod, Android, Gradle, Node, Python, Web, Rust, Go) from marker files — works for created, imported, or cloned projects.

## Markdown preview
- **GitHub-accurate, offline preview**: the **Preview** action opens `MarkdownPreviewTab`, which now
  converts Markdown to HTML with a self-contained, dependency-free converter (`MarkdownToHtml`) and
  renders it in a WebView styled with a bundled GitHub-flavored stylesheet (`GithubMarkdownStyle`,
  light + dark). This matches GitHub far more closely than the old TextView could — proper headings
  with rules, **GFM tables** with column alignment, fenced code blocks with backgrounds, blockquotes,
  **task lists** (`- [ ]` / `- [x]`), nested lists, horizontal rules, links/autolinks and images.
  It stays fully offline (no CDN) and safe (JavaScript disabled, `javascript:` URLs stripped,
  everything HTML-escaped); relative images/links resolve against the file's folder. Animated
  (Crossfade) loading→content transitions are retained.
- The in-app `SimpleMarkdownRenderer` (shared with LSP hovers/extension READMEs) is left untouched.
- The legacy WebView preview's HTML (still used by the "Run" runner) keeps its GitHub-style container.

## Git (VS Code-style)
- **Diff viewer**: tap a changed file to see a color-coded unified diff (HEAD → working tree) via jgit `DiffFormatter`.
- **Discard changes**: long-press a file → confirm → restore from HEAD (or remove untracked/added). Destructive, gated by confirmation.
- **Stage via checkbox**, tap = diff (matches VS Code); plus existing commit / commit&push / push / pull / fetch / branch / amend.
- **UI polish**: per-section change **count badges** and a richer empty state.
- **Initialize Git repository** option during project creation (init + initial commit).

## Project-aware Run button
- The editor **Run (play) button is now project-aware** (`ProjectRunner`, `RunCommand`): instead of
  blindly running a single file, it detects the project type (`ProjectTypeDetector`) of the open
  file's project and acts accordingly, always running from the **project's own folder** in the
  terminal sandbox:
  - **Python** – runs the open `.py` file, or falls back to `main.py`/`app.py`/`run.py`/`manage.py`;
    installs `requirements.txt` first when present.
  - **Node.js** – `npm install` (if needed) then `npm start`, or `node index.js`.
  - **Fabric / Forge / generic Gradle** – checks the JDK is installed, then runs `./gradlew build`
    and surfaces any build errors directly in the terminal.
  - **Rust** – `cargo run`. **Go** – `go run .`.
  - **Static web** – opens the existing in-app HTML preview.
  - **Android & unidentified projects** – the button is **hidden** (Android needs the full SDK and a
    different flow; unknown projects have no meaningful run command).
- Missing toolchains produce a clear message pointing to the **Dependencies** download dialog instead
  of a cryptic shell error.
- New `project_runner.sh` terminal asset implements the per-type run/build logic (no `set -e`, so
  errors stay visible; prints a DONE/FAILED result banner with the exit code).
- **Reliable working directory**: shared-storage project paths (`/storage/emulated/0/…`) are mapped
  to `/sdcard/…`, which the sandbox binds reliably, and `project_runner.sh` retries the `/sdcard`
  form before failing — fixing the "Cannot enter project directory" error for Documents projects.
- `./gradlew` is `chmod +x`'d before building, with a `bash ./gradlew` fallback if the exec bit
  can't be set.

## Editor tabs (project scoping)
- **"Show all files" toggle** in the editor toolbar / top-right overflow (`ToggleShowAllFilesCommand`,
  default **off**). When off, the open tabs are **scoped to the project/directory selected in the
  drawer** — like an IDE, each project shows only its own open files so files from different
  directories don't get mixed together. When on, every open file is shown at once (the classic
  behaviour). Persisted via `Settings.show_all_files`; a one-time migration adds the toggle to
  existing toolbars.

## Editor input
- **Cursor now follows the on-screen keyboard.** When the IME moves the caret (e.g. Gboard's
  space-bar swipe / cursor control), the editor scrolls to keep the caret visible
  (`EditorInputConnection.setSelection` now requests visibility for IME-driven moves). It's a no-op
  when the caret is already on screen, so it only scrolls when the caret would otherwise be off-view.

## Performance
- Stable list `key` for the projects list to avoid needless recomposition.
- Editor: enabled `cacheRenderNodeForLongLines` for smoother scrolling/typing on long-line files (hardware-accelerated drawing is already on by default in this editor).

## Storage / permissions (honest)
- `StorageUtils` adds shared-storage detection and a **real exec-capability probe**.
- Storage permission is **re-checked on resume**, so granting "All files access" is picked up without restarting.
- **Hard truth**: Android shared storage (`/sdcard`, i.e. Documents/Downloads) is mounted `noexec` and ignores `chmod`, so Gradle/native builds cannot run there. Build-type projects are therefore routed to the exec-capable sandbox, with a clear warning otherwise. This is an OS limitation, not a bug.

## CI / build
- **Release signing fallback**: when signing secrets are absent (forks), `assembleRelease` falls back to the bundled testkey instead of failing with "missing storePassword".
- **Clone build variant** (`com.rk.xededitor.pro`, label "Xed PRO", testkey-signed): installs **alongside** the official app instead of replacing it.
- **`build-clone-apk.yml`** workflow builds and uploads the clone APK as an artifact (no secrets required).

## Known limitations (not faked)
- The generated Android project is a complete Android Studio "Empty Activity" project; the only non-text asset (the launcher PNG/WebP bitmaps) is replaced by functionally-equivalent **vector adaptive icons** (binaries can't be authored as text).
- The **Android SDK** install is heavy and architecture-dependent on a phone (sdkmanager/aapt2 are Linux binaries running under proot); it's offered as an explicit optional download — install it only if you want on-device builds.
- Editor input uses the upstream sora-editor; hardware-accelerated drawing and long-line render caching are enabled, but the core input pipeline is unchanged.
