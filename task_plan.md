# 任务计划：Xed-Editor 终端功能系列修改

## 目标
完成终端的 5 项功能修改（默认主目录、打开文件夹方式、输入法状态、SAF 主目录暴露、终端界面恢复），并打包验证 APK。

## 当前阶段
阶段 8（打 Release 包）— complete（阶段 6 待确认事项仍挂起）

## 各阶段

### 阶段 1：终端默认主目录改为 sandbox/root
- [x] FileConstants.kt 新增 `sandboxRootDir()` = `sandboxDir()/root`
- [x] MkSession.kt：HOME=/root、EXT_HOME=sandboxRootDir、getPwd 默认 /root
- [x] ubuntuProcess.kt：HOME=/root、EXT_HOME=sandboxRootDir、绑定 /root
- [x] sandbox.sh / setup.sh：默认工作目录 `-w /root`
- **状态：** complete

### 阶段 2：「在终端中打开文件夹」改为当前终端 cd（不新建终端）
- [x] 新建 TerminalNavigation.kt（shellQuote / navigateCurrentTerminalTo / openDirectoryInTerminal）
- [x] TerminalAction 改用 openDirectoryInTerminal（当前终端有会话则 cd，否则才新建终端）
- [x] MkSession.getPwd 移除 project_as_pwd 的 WKDIR 逻辑（保留 pendingCommand/cwd 处理）
- **状态：** complete（后续按用户要求回滚了自动 cd 部分，见阶段 2.1）

### 阶段 2.1：打开终端不要自动切换目录到文件所在文件夹（回滚）
- [x] 删除 TerminalScreen.kt 中会话 attach 后自动发送 `cd <文件目录>` 的逻辑
- [x] 删除 MkSession.kt 中 `getCurrentFileDir()` 及相关 import
- **状态：** complete

### 阶段 3：编辑器唤出输入法使用默认输入状态
- [x] Editor.kt `showSuggestions()`：不再使用 `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`（会强制英文/密码状态）
- [x] 改为 `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_MULTI_LINE`（开启建议）
- [x] 关闭建议时用 `TYPE_CLASS_TEXT | MULTI_LINE | NO_SUGGESTIONS`（保持默认输入状态且关闭建议）
- **状态：** complete

### 阶段 4：SAF「显示主目录」暴露终端系统目录
- [x] DocumentProvider.kt：BASE_DIR 由 sandboxRootDir() 改为 sandboxDir()（终端系统目录 /sandbox）
- [x] 搜索范围限制同步改为 sandboxDir()
- **状态：** complete

### 阶段 5：从终端界面离开后，下次打开自动进入终端
- [x] Settings.kt 新增 `last_screen_terminal` 偏好（默认 false）
- [x] Terminal.kt onResume 置 `Settings.last_screen_terminal = true`
- [x] MainActivity.kt onResume 置 false；onCreate 检查标记（且为桌面 MAIN 启动）时自动拉起 Terminal
- **状态：** complete

### 阶段 6：Git 初始化（用户新增）
- [x] `git init` 初始化项目根仓库（原无 .git）
- [ ] 确认 soraX 子模块处理方式（独立仓库，暂保持嵌套）
- [ ] 确认首次 commit 范围（是否含 build/、.gradle/、task_plan.md 等）
- **状态：** in_progress

### 阶段 7：终端快捷按键栏 - 改为 Shift（用户新增）
- [x] ExtraKeys.kt `DEFAULT_TERMINAL_EXTRA_KEYS`：`-`（popup `|`）→ `SHIFT`
- [x] Settings.kt 私有 `DEFAULT_TERMINAL_EXTRA_KEYS` 同步修改（两处默认值保持一致）
- [x] 确认 Shift 作为 SpecialButton 原生支持（VirtualKeysView toggle，无需改 Listener/Client/BackEnd）
- [x] `:app:assembleDebug` BUILD SUCCESSFUL
- **状态：** complete

### 阶段 8：打 Release 包（用户新增）
- [x] 用 testkey 走 CI 同款 `assembleRelease`（正式 keystore 不在本机，未改仓库文件）
- [x] 确认 APK 产物：`app-release.apk` 24,243,800 B，V2 签名验证通过（testkey）
- [x] 清理临时签名文件（app/tmp、E:\tmp）
- **状态：** complete

## 关键问题
1. ~~main 模块与 terminal 模块依赖方向（core:main 不能引用 features:terminal）~~ → 已解决：用 `Intent().setClassName` 字符串方式启动，避免编译期依赖
2. ~~MainActivity.onCreate 自动启动终端与 onResume 清除标记的时序冲突~~ → 已解决：onCreate 中同步判定并消费标记，post 只负责启动

## 阶段 9：为残留 UI 字符串补齐多语言翻译（新增）
- [x] 定位 5 个只在部分语言存在、default(EN) 缺失的孤儿 key：icon_pack_missing_fields / verified / samsung_proot_warning / terminal_degraded_warning / crashed（上游功能移除后翻译残留）
- [x] 恢复英文基准原文（crashed/samsung/terminal_degraded 曾存在于上游 default；icon_pack/verified 为重构译文）
- [x] 用 Python 脚本向 core/resources 全部 40 个语言文件 + default(EN) 补齐缺失 key（共 37 文件、+159 行）
- [x] XML 全部合法解析；`:app:assembleDebug` BUILD SUCCESSFUL
- **状态：** complete

## 已做决策
| 决策 | 理由 |
|------|------|
| 新增 `sandboxRootDir()`（`sandboxDir()/root`）作为终端主目录 | 用户要求默认主目录为 sandbox 下 root |
| `openDirectoryInTerminal` 优先 cd 当前终端，无会话才新建 | 用户要求不新建终端，在当前终端输命令 |
| 打开终端时不做自动 cd 到文件目录 | 用户最终确认不需要自动切换目录 |
| `showSuggestions(false)` 用 NO_SUGGESTIONS 代替 VISIBLE_PASSWORD | 保持默认输入状态，不强制英文 |
| SAF 暴露 `sandboxDir()`（/sandbox）而非 sandbox/root | 用户指定终端系统目录 /sandbox |
| 用 `last_screen_terminal` 偏好 + Activity onResume 维护 | 在进程被杀后仍能恢复终端界面 |
| 用 `Intent().setClassName("com.rk.activities.terminal.Terminal")` 启动 | core:main 不能编译期依赖 features:terminal |

## 遇到的错误
| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| proot 原生构建找不到 awk（Windows） | 1 | 将 `C:\Program Files\Git\usr\bin` 加入 PATH |
| `getParentFile()` 是 suspend 函数，非 suspend 上下文编译失败 | 1 | 改用 `FileWrapper.file.parentFile` 直接访问 |
| filesystem-replaceedit 在 Terminal.kt 引入多余 `}`（结构破坏） | 1 | 读取确认后删除多余 `}` 并修正缩进 |
| filesystem-replaceedit 在 FileConstants.kt 引入多余 `}` | 1 | 读取确认后删除多余 `}` |

## 备注
- 构建命令（Windows 需先加 Git 路径）：`$env:Path = "C:\Program Files\Git\usr\bin;C:\Program Files\Git\bin;" + $env:Path; gradlew.bat :app:assembleDebug`
- APK 输出：`app/build/outputs/apk/debug/app-debug.apk`
- 项目根目录不是 Git 仓库（soraX 子模块是独立仓库）
