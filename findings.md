# 发现与决策

## 需求
- 终端默认主目录应为 sandbox 下的 root 目录（`sandboxDir()/root`）
- 「在终端中打开文件夹」不新建终端，而是在当前终端输入 `cd` 命令
- 打开终端时**不要**自动切换目录到编辑器当前文件所在文件夹
- 编辑器唤出输入法应使用默认输入状态，而不是英文/密码输入状态
- 终端设置中「显示主目录」（SAF 暴露）应暴露终端系统目录 `/sandbox` 而不是 `sandbox/root`
- 用户在终端界面离开应用（未完全退出）后，下次打开应用自动进入终端界面
- 终端快捷按键栏的 `-` 键改为 `SHIFT` 键

## 研究发现
### 路径与沙箱结构（核心）
- `localDir()` = `.../local`；`sandboxDir()` = `.../local/sandbox`（PRoot 根文件系统，即终端系统目录 /sandbox）
- `sandboxHomeDir()` = `.../local/home`（旧主目录，绑定到沙箱内 /home）
- `sandboxRootDir()` = `.../local/sandbox/root`（新增，绑定到沙箱内 /root，作为终端主目录）
- 沙箱内 `/data` 被绑定，因此 Android 原始绝对路径（`/data/data/...`）在沙箱内可直接访问
- `getPwd()` 中 `removePrefix(localDir)` 只对 `localDir` 直下目录正确（如 /home），对 sandbox 内文件不正确（/sandbox/... 是错的）；因此 cd 导航统一使用原始绝对路径

### 终端会话与输入
- `TerminalSession.write(String)` 继承自 `TerminalOutput`，回车用 `\u000D`（CR）
- `terminalView`（全局 WeakReference）的 `currentSession` 即当前会话，可直接 `write()` 发命令
- Terminal Activity 是 `singleTask`；`onNewIntent` → `handleIntent`（cwd 存在时创建/切换会话）

### 输入法
- `Editor.showSuggestions(yes)` 控制 `inputType`；原 `false` 分支用 `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`（0x90，无 CLASS 位）→ 输入法进入密码/英文状态
- `onCreateInputConnection`：`inputType != 0 ? inputType : TYPE_CLASS_TEXT|MULTI_LINE`

### 模块依赖
- `core:main`（MainActivity、Settings、DocumentProvider）**不能**编译期引用 `features:terminal`（Terminal Activity）
- 跨模块启动 Terminal 需用 `Intent().setClassName(ctx, "com.rk.activities.terminal.Terminal")` 字符串方式
- TerminalFeature 通过 `TerminalLauncher.handler` 注册回调，`pendingCommand` 传递启动参数

### 终端额外按键（VirtualKeysView）
- 默认布局定义有**两处**：`features/terminal/.../settings/terminal/ExtraKeys.kt`（`const DEFAULT_TERMINAL_EXTRA_KEYS`，公开，被 `TerminalScreen` 与 `Settings.kt` 引用）与 `core/main/.../settings/Settings.kt`（`private const DEFAULT_TERMINAL_EXTRA_KEYS` 同名）；两者必须保持同步
- 特殊键（`SpecialButton`：CTRL/ALT/SHIFT/FN）由 `VirtualKeysView.isSpecialButton` 识别（默认 mSpecialButtons 已含全部四个），点击只 toggle `SpecialButtonState`，**不写入终端**
- SHIFT 状态由 `TerminalBackEnd.readShiftKey()` 读取（TerminalBackEnd.kt:152-155），供键盘输入使用
- 默认布局中 SHIFT 插在 `ESC` 与 `HOME` 之间；改动只影响默认布局，用户已自定义时保留旧值（重置按钮恢复新默认）
- 额外按键加载失败时会 toast `invalid_terminal_extra_keys` 并回退到 `DEFAULT_TERMINAL_EXTRA_KEYS`（TerminalScreen.kt:189-198）

### 应用启动流程
- MainActivity 为 launcher + `singleTask` + `alwaysRetainTaskState`
- 进程被杀后冷启动走 `onCreate`；后台恢复走任务栈还原（Terminal 在栈顶则自然显示）
- `Settings` 用 `CachedPreference`（SharedPreferences 封装），onResume 中读写是安全的

### Release 签名与 Gradle 路径（Windows）
- `app/build.gradle.kts` release signingConfig：正式 keystore 只在 CI（secrets → `/tmp/xed.keystore` + `/tmp/signing.properties`，`GITHUB_ACTIONS=true` 分支）或本机 `/home/rohit/Android/xed-signing/`（Linux）存在；本机 Windows 无正式签名
- **Gradle 中 `File("/tmp/...")` 以 `/` 开头的路径按项目目录相对解析**（非盘符根目录）→ 设 `GITHUB_ACTIONS=true` 时 keystore 实际须放 `<app 模块>/tmp/xed.keystore`，否则 `signingConfigData.storeFile` 报文件不存在
- 本机临时 Release 方案：复制 `app/testkey.keystore` 到 `app/tmp/xed.keystore` + `GITHUB_ACTIONS=true`，走 `assembleRelease`，用 testkey 签名（V2）
- 验证签名：`E:\Android-Sdk\build-tools\<v>/apksigner.bat verify --print-certs app-release.apk`
- 正式发布签名与 testkey 不同；testkey 产物仅适合自用/测试分发

## 技术决策
| 决策 | 理由 |
|------|------|
| 新增 `sandboxRootDir()` 常量 | 统一表示终端主目录，避免硬编码 |
| 新增 `TerminalNavigation.kt` 工具 | 统一 cd 导航逻辑，供 TerminalAction 与未来复用 |
| `openDirectoryInTerminal`：有会话→cd，无会话→startActivity(cwd) | 满足「不新建终端，当前终端输入命令」 |
| 移除 getPwd 的 project_as_pwd WKDIR 逻辑 | 打开终端不再自动切换到文件目录 |
| 终端界面恢复用 `last_screen_terminal` 偏好 + onCreate 消费标记 | 进程被杀后仍能恢复；onResume 清除避免误恢复 |
| MainActivity.onCreate 中同步判定并消费标记，post 仅负责启动 | 避免 onResume 清除标记导致的时序竞态 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| Windows 无 awk，proot 原生构建失败 | Git for Windows 的 `usr/bin` 提供 awk，构建时加入 PATH |
| `FileObject.getParentFile()` 是 suspend | 对 `FileWrapper` 直接用 `file.file.parentFile`（java.io.File） |
| 编辑工具在 Kotlin 文件引入多余 `}` | 每次编辑后用 filesystem-read 复核结构，删除多余括号 |
| core:main 无法引用 Terminal 类 | `Intent().setClassName` 字符串类名启动 |
| 5 个 UI key 只在部分语言存在、default(EN) 缺失 | 从上游 git 历史恢复英文基准 + Python 脚本批量补齐 40 语言文件（37 文件、+159 行） |
| CI `packageRelease`：SigningConfig release 缺 storePassword | fork 的 KEYSTORE/PROP secrets 为空 → 解码出空 signing.properties；在 build.gradle.kts 的 release signingConfig 中「凭据完整才用正式 keystore，否则回退内置 testkey」，保证构建不失败（reproducible.yml 的 sed 删行逻辑不受影响） |

## GitHub Actions 签名补充
- GitHub Actions secrets **不随 fork 继承**：`${{ secrets.KEYSTORE }}` 为空 → `echo "" | base64 -d > /tmp/signing.properties` 因 GNU base64 忽略空行而**退出码 0**、生成**空文件** → Properties.load 全空 → storePassword null → 无条件赋值 signingConfig 时 packageRelease 崩溃（报 missing storePassword）
- 本机 debug 与 release 回退都指向 `app/testkey.keystore`（file(layout.buildDirectory.dir("../testkey.keystore"))）
- 正式签名需在 fork Settings→Secrets 配置 KEYSTORE/PROP（base64 编码的 keystore 与 signing.properties）

## 资源
- 构建：`gradlew.bat :app:assembleDebug`（需 PATH 含 Git usr/bin）
- APK：`app/build/outputs/apk/debug/app-debug.apk`
- 关键文件：`core/main/.../file/FileConstants.kt`、`core/main/.../DocumentProvider.kt`、`core/main/.../editor/Editor.kt`、`core/main/.../activities/main/MainActivity.kt`、`core/main/.../settings/Settings.kt`、`features/terminal/.../TerminalFeature.kt`、`features/terminal/.../terminal/MkSession.kt`、`features/terminal/.../terminal/TerminalScreen.kt`、`features/terminal/.../terminal/TerminalNavigation.kt`（新增）、`features/terminal/.../activities/terminal/Terminal.kt`、`features/terminal/.../exec/ubuntuProcess.kt`、`features/terminal/src/main/assets/terminal/{sandbox.sh,setup.sh}`

## 视觉/浏览器发现
<!-- 关键：每执行2次查看/浏览器操作后必须更新此部分 -->
<!-- 多模态内容必须立即以文本形式记录 -->
- 无（全部为本地代码修改与构建验证）
