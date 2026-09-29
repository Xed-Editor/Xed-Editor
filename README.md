## Xed-Editor

<img src="/fastlane/metadata/android/en-US/images/icon.png" alt="Xed-Editor Icon" width="90" height="90" align="left"/>

**Xed-Editor** is a versatile and extensible text editor for Android, featuring syntax highlighting,
LSP-powered code intelligence, a built-in terminal, extensions, and fast project-wide tools for
efficient editing.

![Android CI](https://github.com/Rohitkushvaha01/Xed-Editor/actions/workflows/android.yml/badge.svg?event=push&style=for-the-badge)
![Download count](https://img.shields.io/github/downloads/Xed-Editor/Xed-Editor/total?label=Downloads)

---

## Website

To learn more about Xed-Editor, including its documentation, extensions, themes, icon packs, and account-related features, visit the official website:
https://xed-editor.app

[![Discord](https://img.shields.io/badge/Discord-5865F2?style=for-the-badge&logo=discord&logoColor=white)](https://discord.gg/6bKzcQRuef)
[![Telegram](https://img.shields.io/badge/Telegram-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/XedEditor)

---

## Download

<div>
  <a href="https://android.izzysoft.de/repo/apk/com.rk.xededitor">
    <img src="https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/com.rk.xededitor&label=IzzyOnDroid&cacheSeconds=33000">
  </a>
  <a href="https://f-droid.org/packages/com.rk.xededitor">
    <img src="https://img.shields.io/f-droid/v/com.rk.xededitor">
  </a>
</div>

- **Latest Alpha Build**: Download from [Actions](https://github.com/Xed-Editor/Xed-Editor/actions/)
- **Latest Stable Build**: Download
  from [Releases](https://github.com/Xed-Editor/Xed-Editor/releases)

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/com.rk.xededitor)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png?ref_type=heads" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/com.rk.xededitor)
[<img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">](https://github.com/Xed-Editor/Xed-Editor/releases/latest)

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

## Build the Project

The app ships in two flavors:

| Flavor      | applicationId        | Extensions module | Notes                                              |
| ----------- | -------------------- | ----------------- | -------------------------------------------------- |
| `community` | `com.rk.xededitor`   | included          | Default flavor. GitHub / F-Droid / IzzyOnDroid.     |
| `playstore` | `com.rk.axion`       | excluded          | Google Play build; minified, without the extension store. |

The `community` flavor is the default flavor (used by CI and by tooling that needs a default
variant). Build a specific flavor with a flavor-qualified task; the unqualified aggregate tasks such
as `assembleDebug` build every flavor.

### Detecting the flavour at runtime

The flavour is declared once per product flavour in `app/build.gradle.kts` as the
`BuildConfig.FLAVOUR` field, and each flavour's `XedApplication` publishes it during startup. Library
modules cannot read the application module's `BuildConfig`, so they should branch on the shared
`com.rk.app.AppFlavour` instead:

```kotlin
// True when this build bundles the extensions module and the user has not disabled it.
if (AppFlavour.current.bundlesExtensions && FeatureRegistry.isEnabled("enable_extension")) {
    // e.g. show the theme/icon-pack/extension store entries
}
```

Unknown or uninitialized flavours fall back to `AppFlavour.COMMUNITY`, so previews and unit tests
still work.

### Bundled terminal rootfs (playstore)

The `playstore` flavour bundles the arm64 Ubuntu rootfs at
`app/src/playstore/assets/arm64-v8a/ubuntu.rootfs`, so the terminal sets up without downloading it.
That flavour therefore supports `arm64-v8a` devices only and shows an error dialog on other ABIs; the
`community` flavour keeps downloading the matching rootfs from the network.

Choose one of the following build methods.

<details>
<summary><strong>Option 1: Build Locally</strong></summary>

Build the **debug APK** (signed with the included test key):

```bash
./gradlew assembleCommunityDebug
```

The compiled APK will be located at `app/build/outputs/apk/community/debug/app-community-debug.apk`.

To build the Play Store variant instead:

```bash
./gradlew assemblePlaystoreRelease
```

</details>

<details>
<summary><strong>Option 2: Build with Docker</strong></summary>

If you don't have the Android SDK or JDK 21 installed locally, you can build the project in a Docker
container:

```bash
DOCKER_BUILDKIT=1 docker build --target export-stage --output ./out .
```

The generated debug APK will be located at `out/community/debug/app-community-debug.apk`.

</details>

---

## Contributing

We welcome contributions! Please read the [`/docs/CONTRIBUTING.md`](/docs/CONTRIBUTING.md) file to
learn how you can get involved.

---

## Translations

Help translate Xed-Editor! Visit [Weblate](https://hosted.weblate.org/engage/xed-editor/) to get
started:

<a href="https://hosted.weblate.org/engage/xed-editor/">
    <img src="https://hosted.weblate.org/widgets/xed-editor/-/multi-auto.svg" alt="Translation Status">
</a>

---

## Contributors

<a href="https://github.com/Xed-Editor/Xed-Editor/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=Xed-Editor/Xed-Editor" />
</a>
