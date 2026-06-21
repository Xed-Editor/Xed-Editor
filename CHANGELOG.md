# Changelog

All notable changes to Xed-Editor are documented here.

Format: `type: description (#PR)` where type is one of:
- **feat** — new feature
- **fix** — bug fix
- **chore** — maintenance
- **refactor** — code restructure
- **perf** — performance
- **style** — formatting
- **ci** — CI/CD
- **docs** — documentation

---

## 3.2.9 (Current)

- fix: inverted snackbar color
- feat: refactor extension system (#1340)
- style: reorder imports and remove debug println
- feat: add toggle for exit confirmation dialog (Closes #1332)
- feat: add auto-closing bracket setting (#1333)
- fix: remove issue title workflow
- Added translation using Weblate (Dutch)
- style: auto-format Kotlin with ktfmt
- fix: lsp server config
- feat: implement plugin download
- feat: improved caching
- feat: use new extension registry
- feat: add integrated color picker (#1280)
- fix: ensure keyboard is hidden on terminal screen disposal
- feat: enhance properties, use slider and migrate from Gson (#1279)
- feat: fix LSP persistence migration issues
- feat: replace Gson with Kotlin Serialization and migrate LSP storage
- feat: refactor `ValueSlider` and improve settings screens
- feat: enhance properties dialog with Git status and file information
- chore: enhance file drawer and remove debug extension (#1278)
- perf: optimize code search and indexing in `SearchViewModel` (#1277)
- fix: preserve insertion order in code search results
- fix: fix code search crashes and improve file stream handling
- ci: safely handle commit messages in Telegram uploads (#1276)
- feat(terminal): fix terminal focus issue & modernize (#1275)
- fix: fix extension detail screen and navigation (backport)

## 3.2.8

- chore: format and cleanup drawable resources (#1273)
- feat: implement extension detail screen (#1265)
- feat: add extension repository button and reviews tab placeholder
- feat: enhance extension discovery with sorting, searching, filtering
- feat: add review section, author icon, refreshable details
- feat: enhance about screen and extension/language settings
- fix: race condition and improving reload command (#1264)
- fix: tab wipe by adding unique keys to pager (#1261)
- feat: implement font management for app, editor, and terminal (#1260)
- fix: improve duplicate issue detection (#1257)
- feat: add URI tap detection and link opening (#1254)

## 3.2.7

- feat: minimap support (#1250)
- fix: compilation issues
- chore: cleanup project (#1242)
- feat: implement multi-file selection and refactor (#1240)
- fix: crash on closing project
- feat: improve drawer UI and animations
- feat: fullscreen-mode, smart toolbar and fix various crashes (#1217)
- fix: terminal restore/backup crash (Closes #1209)
- fix: LSP status not showing if no icon (Closes #1214)
- feat: enhance LSP management and infrastructure (#1208)

## 3.2.6

- feat: enhance editor theming and LSP logging (#1198)
- fix: editor not loading content (#1196)
- fix: LSP project and workspace paths (#1195)
- fix: extension language server installation (#1193)
- fix: extension language servers not connecting (#1192)
- feat: R language support & dynamic FileType registration (#1191)
- fix: index database crash and improve binary detection (#1190)
- feat: add proper exclusion system (#1169)

## 3.2.5

- feat: improve LSP functionality (#1031)
- fix: crashes (#1165)
- feat: add replace everywhere button (#1164)
- feat: center terminal leave warning
- fix: remove unwanted READ_PHONE_STATE permission (#1163)
- feat: add external server editing (Closes #1116)
- feat: add app logs to debug options
- feat: add LSP server detail page
- feat: add LSP logging mechanism
- feat: add auto-completion on enter setting
- fix: duplicate language server connection

## Earlier

See [git log](https://github.com/algospider/Xed-Editor/commits/dev) for earlier changes.
