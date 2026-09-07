# 进度日志

## 会话：2026-09-07（Git 初始化 + 终端 Shift 键 + Release 包）
### 阶段 8：打 Release 包
- **状态：** complete
- **开始时间：** 2026-09-07
- 执行的操作：
  - 分析 app/build.gradle.kts 签名配置：release signingConfig 仅 CI（`/tmp/signing.properties` + `/tmp/xed.keystore`，GITHUB_ACTIONS=true）或本机 `/home/rohit/...` 存在才签名；正式 keystore 不在本机
  - 采用 CI 同款路径方案：临时复制 `app/testkey.keystore`（3035 B）→ 签名文件，设 `GITHUB_ACTIONS=true` 走 `/tmp` 分支
  - 关键发现：Gradle 中 `File("/tmp/...")` 以 `/` 开头的路径按**项目目录相对**解析 → 实际需要 keystore 在 `app/tmp/xed.keystore`（首次失败：signingConfigData storeFile 不存在）
  - `:app:assembleRelease`（`--console=plain` 日志存 E:\tmp\release-build.log）→ **BUILD SUCCESSFUL in 6s**（581 up-to-date）
  - `apksigner verify --print-certs` → V2 签名验证通过（CN=Android / testkey，SHA-256 a40da8...）
  - APK 24,243,800 B（~23MB）；复制为 `app/xed-editor-3.4.4-release.apk`（CI 命名风格）
  - 清理：`app/tmp/`、`E:\tmp\signing.properties`、`E:\tmp\xed.keystore`、release-build.log
- 创建/修改的文件：
  - `app/build/outputs/apk/release/app-release.apk`（产物）
  - `app/xed-editor-3.4.4-release.apk`（带版本号副本）
  - task_plan.md（新增阶段 8）
- 注意：APK 用 **testkey** 签名，与正式发布签名不同（正式发布需在 CI 用 secrets 或本机正式 keystore 重新构建）
- 仓库文件改动：无（签名文件未提交，均已清理）

## 会话：2026-09-07（Git 初始化 + 终端 Shift 键）
### 阶段 6：Git 初始化
- **状态：** in_progress（挂起，等待用户确认子模块与 commit 范围）
- **开始时间：** 2026-09-07
- 执行的操作：
  - `Test-Path .git` 确认无仓库（False），`git --version 2.53.0.windows.2`
  - `git init` → `Initialized empty Git repository in E:/Xed-Editor-main/.git/`，当前分支 master
  - `git status` 确认全部文件为 untracked（??），`.gitmodules` 声明 soraX 子模块
  - 确认 `soraX/.git` 存在且 `git -C soraX status` 干净（独立仓库，保持嵌套未注册为 gitlink）
- 创建/修改的文件：
  - `.git/`（新增仓库）
  - `task_plan.md`（新增阶段 6）
- 待确认：
  - soraX 按 submodule 注册还是保持独立嵌套
  - 首次 commit 范围（build/、.gradle/、规划文件是否纳入）

### 阶段 7：终端快捷按键栏 - 改为 Shift
- **状态：** complete
- **开始时间：** 2026-09-07
- 执行的操作：
  - 定位默认额外按键定义两处：`features/terminal/.../settings/terminal/ExtraKeys.kt`（const DEFAULT_TERMINAL_EXTRA_KEYS）与 `core/main/.../settings/Settings.kt`（private const，同名）——Settings 引用 ExtraKeys 的 const，两处需同步
  - 确认 VirtualKeysView 的 SHIFT 原生支持：`isSpecialButton`（mSpecialButtons 默认含 CTRL/ALT/SHIFT/FN）→ 点击只 toggle `SpecialButtonState`（VirtualKeysView.java:525-536），不写入终端
  - `TerminalBackEnd.readShiftKey()` 读取 SHIFT 状态（TerminalBackEnd.kt:152-155），无需改 Listener/Client/BackEnd
  - ExtraKeys.kt：删除 `{"key":"-","popup":"|"}`，替换为 `"SHIFT"`（位于 ESC 与 HOME 之间）
  - Settings.kt：同名默认常量同步替换
  - `:core:main:compileDebugKotlin :features:terminal:compileDebugKotlin` BUILD SUCCESSFUL（无新警告）
  - `:app:assembleDebug` BUILD SUCCESSFUL（APK 生成）
- 创建/修改的文件：
  - features/terminal/src/main/java/com/rk/settings/terminal/ExtraKeys.kt
  - core/main/src/main/java/com/rk/settings/Settings.kt
  - task_plan.md（新增阶段 7）
- 注意：改动仅影响**默认布局**；用户已自定义 extra keys 时保留旧值（可点重置恢复新默认）

## 会话：2026-08-18（终端功能系列修改 + APK 打包）

### 阶段 1：终端默认主目录改为 sandbox/root
- **状态：** complete
- 执行的操作：
  - FileConstants.kt 新增 `sandboxRootDir()`（= `sandboxDir().child("root")`，自动建目录）
  - MkSession.kt：`HOME=/root`、`EXT_HOME=${sandboxRootDir}`、`getPwd` 沙箱默认返回 `/root`、非沙箱返回 `sandboxRootDir().absolutePath`；移除未用 import
  - ubuntuProcess.kt：`HOME=/root`、`EXT_HOME=sandboxRootDir`、`getDefaultBindings` 绑定 `sandboxRootDir → /root`
  - sandbox.sh / setup.sh：`-w /` → `-w /root`
- 创建/修改的文件：
  - core/main/src/main/java/com/rk/file/FileConstants.kt
  - features/terminal/src/main/java/com/rk/terminal/MkSession.kt
  - features/terminal/src/main/java/com/rk/exec/ubuntuProcess.kt
  - features/terminal/src/main/assets/terminal/sandbox.sh
  - features/terminal/src/main/assets/terminal/setup.sh

### 阶段 2：「在终端中打开文件夹」改为当前终端 cd
- **状态：** complete
- 执行的操作：
  - 新建 TerminalNavigation.kt：`shellQuote` / `navigateCurrentTerminalTo`（`session.write("cd '...'\r")`）/ `openDirectoryInTerminal`
  - TerminalFeature.kt：`TerminalAction.action()` 改用 `openDirectoryInTerminal(ctx, file.getAbsolutePath())`
  - MkSession.kt：getPwd 移除 project_as_pwd WKDIR 分支，新增 `getCurrentFileDir()`（后被删除）
  - TerminalScreen.kt：会话 attach 后发送 cd（后被删除）
- 创建/修改的文件：
  - features/terminal/src/main/java/com/rk/terminal/TerminalNavigation.kt（新增）
  - features/terminal/src/main/java/com/rk/TerminalFeature.kt
  - features/terminal/src/main/java/com/rk/terminal/MkSession.kt
  - features/terminal/src/main/java/com/rk/terminal/TerminalScreen.kt

### 阶段 2.1：打开终端不要自动切换目录（回滚）
- **状态：** complete
- 执行的操作：
  - 删除 TerminalScreen.kt 中 attach 后的自动 `cd` 块（`getCurrentFileDir()?.let { session.write(...) }`）
  - 删除 MkSession.kt 的 `getCurrentFileDir()` 及 MainActivity/FileWrapper/EditorTab 未用 import
  - 确认：`getCurrentFileDir` / `shellQuote` 无残留引用（shellQuote 仅 TerminalNavigation.kt 内部使用）
- 创建/修改的文件：
  - features/terminal/src/main/java/com/rk/terminal/TerminalScreen.kt
  - features/terminal/src/main/java/com/rk/terminal/MkSession.kt

### 阶段 3：输入法默认输入状态
- **状态：** complete
- 执行的操作：
  - Editor.kt `showSuggestions()`：yes → `TYPE_CLASS_TEXT|MULTI_LINE`；no → `TYPE_CLASS_TEXT|MULTI_LINE|NO_SUGGESTIONS`（不再用 VISIBLE_PASSWORD）
- 创建/修改的文件：
  - core/main/src/main/java/com/rk/editor/Editor.kt

### 阶段 4：SAF「显示主目录」暴露终端系统目录
- **状态：** complete
- 执行的操作：
  - DocumentProvider.kt：BASE_DIR 由 sandboxRootDir() 改为 sandboxDir()（先改为 sandboxRootDir，再按用户要求改为 sandboxDir）
  - 搜索范围 `isInsideHome` 同步改为 sandboxDir()
- 创建/修改的文件：
  - core/main/src/main/java/com/rk/DocumentProvider.kt

### 阶段 5：从终端离开后下次打开自动进终端
- **状态：** complete
- 执行的操作：
  - Settings.kt 新增 `last_screen_terminal`（CachedPreference，默认 false）
  - Terminal.kt：onResume 增加 `Settings.last_screen_terminal = true`；补 import Settings；修复编辑引入的多余 `}` 与缩进
  - MainActivity.kt：onResume 增加 `Settings.last_screen_terminal = false`；onCreate 中若 `last_screen_terminal && shown_disclaimer && (action==null||MAIN)` 则消费标记并 `window.decorView.post { startActivity(Intent().setClassName(this, "com.rk.activities.terminal.Terminal")) }`
- 创建/修改的文件：
  - core/main/src/main/java/com/rk/settings/Settings.kt
  - features/terminal/src/main/java/com/rk/activities/terminal/Terminal.kt
  - core/main/src/main/java/com/rk/activities/main/MainActivity.kt

## 测试结果
| 测试 | 输入 | 预期结果 | 实际结果 | 状态 |
|------|------|---------|---------|------|
| :app:assembleDebug（首次） | 无 awk 环境 | 成功 | 失败：proot 构建缺 awk | 失败→修复 |
| :app:assembleDebug（PATH 加 Git usr/bin 后） | gradlew assembleDebug | 成功 | BUILD SUCCESSFUL（含全部改动） | 通过 |
| Kotlin 编译（getCurrentFileDir 非 suspend） | compileDebugKotlin | 成功 | 失败：getParentFile() 是 suspend | 失败→修复 |
| :app:assembleDebug（终态） | gradlew assembleDebug | 成功 | BUILD SUCCESSFUL，APK 生成 | 通过 |

## 错误日志
| 时间戳 | 错误 | 尝试次数 | 解决方案 |
|--------|------|---------|---------|
| 2026-08-18 | proot 原生构建 `awk` 未找到（llvm-readelf \| awk） | 1 | `$env:Path` 前置 `C:\Program Files\Git\usr\bin` 后重构建 |
| 2026-08-18 | MkSession.kt:171 `getParentFile()` suspend 调用报错 | 1 | 用 `FileWrapper.file.parentFile` 替代 |
| 2026-08-18 | replaceedit 在 FileConstants.kt 引入多余 `}` | 1 | 读取复核后删除 |
| 2026-08-18 | replaceedit 在 Terminal.kt 引入多余 `}` 且缩进错乱 | 1 | 读取复核，删除多余 `}` 并修正缩进 |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 阶段 8（Release 包，complete） |
| 我要去哪里？ | 阶段 6 Git 初始化待确认项（子模块、commit 范围） |
| 目标是什么？ | 终端 5 项功能 + Shift 键已交付，Release APK 已打包 |
| 我学到了什么？ | 见 findings.md（沙箱路径体系、TerminalSession.write、模块依赖方向、输入法 inputType、VirtualKeysView 特殊键机制、Gradle `/`路径相对项目目录） |
| 我做了什么？ | 见上方各阶段记录（5 项功能 + Shift 键 + Release 打包 + git init） |
