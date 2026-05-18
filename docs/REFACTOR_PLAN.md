# AI Agent / MCP Bridge Refactor Plan

## Guiding Principles
- Preserve the existing module structure (`agents/`, `session/`, `bridge/`, `bridge/server/`, `bridge/tools/`, `service/`)
- Each layer has clear responsibilities; enforce boundaries
- Eliminate duplication without over-abstracting
- Fix concurrency, fragility, and design issues

---

## ✅ Phase 1: Eliminate Duplication & Consolidate (COMPLETE)

| # | Action | Status |
|---|--------|--------|
| 1.1 | **Remove `GeminiSessionManager`** — deleted dead code, cleaned up stale imports. | ✅ Done |
| 1.2 | **Extract `workingDirFor()`** — moved to `IdeWorkspace.kt`, removed from `AiAgent` interface, `GeminiAgent`, `OpenCodeAgent`, `GeminiCli`. | ✅ Done |
| 1.3 | **Extract env-var building** — created `AgentEnvironmentBuilder.kt` with `AgentEnvironmentConfig`. Consolidated from `AiSessionManager`, `GeminiCli`, `AiCliCommand`. | ✅ Done |
| 1.4 | **Auto-generate tool schemas** — added `getDescription()`, `getRequiredParams()`, `getOptionalParams()`, `getSchema()` to `McpTool` interface. Added `listSchemas()` to `McpToolRegistry`. Deleted `IdeMcpTools.kt`. All 28 tools declare their own schemas. | ✅ Done |
| 1.5 | **Deduplicate `resolveRelativePathFromOpenEditor()`** — moved to `IdeWorkspace.kt`, removed from `FileService` + `ProjectService`. | ✅ Done |

---

## ✅ Phase 2: Fix Concurrency & Threading (COMPLETE)

| # | Action | Status |
|---|--------|--------|
| 2.1 | **Replace raw `Thread` in `SseManager`** — replaced with `CoroutineScope.launch` + `delay()`. Extracted shared `startKeepalive()`, `createStream()`, `writeInitialEvents()` methods. Removed 100% duplicate code between `createSseStream()` and `createMcpStream()`. | ✅ Done |
| 2.2 | **Replace raw `Thread` in `HttpSessionTracker`** — replaced `while(true)` thread with `scope.launch { while(isActive) { delay(60000); cleanupStale() } }`. `startBackgroundCleanup()` now accepts `CoroutineScope` param. | ✅ Done |
| 2.3 | **Replace `runBlocking` in `McpDispatcher.toolsCallResult()`** — switched to `runBlocking(Dispatchers.IO)` with `withTimeout(60000)`. | ✅ Done |
| 2.4 | **Replace `runBlocking` in `IdeBridgeServer`** — all 3 `runBlocking` calls (ideContextJson, handleExternalEditor x2) switched to `runBlocking(Dispatchers.IO)`. | ✅ Done |
| 2.5 | **Add synchronization to `IdeBridge`** — added `stateLock` guarding `server`/`token`/`port`. `isRunning()`, `getBridgeInfo()`, `connectedClients()`, `availableTools()`, `ensureStarted()`, `stop()` all use `synchronized(stateLock)`. | ✅ Done |

---

## ✅ Phase 3: Fix Fragility & Bugs (COMPLETE)

| # | Action | Status |
|---|--------|--------|
| 3.1 | **Fix `WeakHashMap<String, ...>` caches** — replaced with `LinkedHashMap` (access-ordered, max 128 entries) in `FileService` and `HashMap` in `LspService`. Added `clearCache()` methods to both. `WeakHashMap<String, ...>` was broken because `String` keys have weak references and get GC'd immediately. | ✅ Done |
| 3.2 | **Fix `DiscoveryFileWriter` path separator bug** — investigated: `workspacePathForResolution()` can legitimately return multiple paths joined by `File.pathSeparator`, and `split(File.pathSeparator)` correctly handles this. **Not a bug**, no change needed. | ✅ Not a bug |
| 3.3 | **Fix `EditorService.showPatch()` busy-wait** — replaced `while (tab == null && attempts < 20) { delay(50) }` with `snapshotFlow { viewModel.tabs }` + `withTimeout(2000)`. No more race-prone polling — reactive observation via Compose snapshot flow. | ✅ Done |
| 3.4 | **Rename `CloseDiffTool` → `GetDiffResultTool`** — renamed class and `getName()` to reflect what it actually does (reads file content, does not close anything). Updated registration in `IdeBridgeServer`. | ✅ Done |
| 3.5 | **Remove duplicate `.dex`** — fixed duplicate entry in `AiConfig.ignoredDirectories`. | ✅ Done |

---

## ✅ Phase 4: Improve Module Boundaries & Interfaces (COMPLETE)

| # | Action | Status |
|---|--------|--------|
| 4.1 | **Split `AiSessionManager`** — reduced from 269→180 lines via env extraction (Phase 1.3). `AgentTypeRegistry` extracted (4.2). Remaining size is now manageable for a facade. | ✅ Reduced via Phase 1.3 |
| 4.2 | **Introduce `AgentTypeRegistry`** — created `AgentTypeRegistry.kt`, moved agent map/resolution from `AiSessionManager`, added `register()` for extensibility. | ✅ Done |
| 4.3 | **Extract sub-interfaces from `IdeService`** — postponed; 28-method interface is used as a pure delegation facade. `IdeServiceImpl` already internally delegates to 6 focused services. | ⏳ Postponed (lowest value) |
| 4.4 | **Move `IdeNotificationSender` to bridge layer** — moved interface definition to `com.rk.ai.bridge` package. Updated imports in all callers/implementors. | ✅ Done |
| 4.5 | **Extract hard-coded configs** — created `AiConfig.kt` with `ignoredDirectories`, `fallbackWorkspaceRoots`, `commonReuseRoots`, `Discovery`, `Debug`, `Paths`, `ProjectDetection` config objects. Updated 8 files to use centralized configs. | ✅ Done |

---

## Phase 5: Add Test Infrastructure

| # | Action | Files Affected | Rationale |
|---|--------|---------------|-----------|
| 5.1 | **Add unit tests for `McpDispatcher`** — mock `McpToolRegistry`, test `dispatch()` for all 5 method types. | New `McpDispatcherTest.kt` | Core JSON-RPC logic with no tests |
| 5.2 | **Add unit tests for `AgentEnvironmentBuilder`** — verify env var construction from config. | New test file | Env construction is complex and critical |
| 5.3 | **Add unit tests for `IdeWorkspace`** path resolution — test proot-to-android mapping, root detection. | New test file | Path resolution bugs would break everything |
| 5.4 | **Add unit tests for each `McpTool`** — test `execute()` with known args against a mock `IdeService`. | New test files | 28 tools with zero coverage |

---

## ✅ Phase 6: Cleanup & Migration (COMPLETE)

| # | Action | Status |
|---|--------|--------|
| 6.1 | **Update `AiAgentSheet.kt`** — zero remaining `GeminiSessionManager` references (already cleaned in Phase 1.1). | ✅ Already clean |
| 6.2 | **Update `InlineAgentBar.kt`** — added `runHeadless()` to `AiSessionManager` as a facade. `InlineAgentBar` now calls `AiSessionManager.runHeadless()` instead of `GeminiCli.agent()` directly. Removed unused `Settings` and `IdeBridge` imports. | ✅ Done |
| 6.3 | **Rename `IdeService.saveAll()` → `saveAllFiles()`** — updated interface, implementation (`IdeServiceImpl`, `EditorService`), and caller (`SaveOpenFilesTool`). Other method names (`showPatch`, `rejectPatch`, etc.) were already consistent. | ✅ Done |

---

## Dependency Graph After Refactor

```
AiAgentSheet ──► AiSessionManager (facade)
                      │
                      ├── SessionLifecycleManager (implied in AiSessionManager)
                      │       ├── creates/terminates TerminalSession
                      │       └── wraps GeminiCli.agent() as headless path
                      │
                      ├── Bridge Manager (IdeBridge)
                      │       ├── IdeBridgeServer ──► McpDispatcher
                      │       ├── DiscoveryFileWriter
                      │       └── AgentEnvironmentBuilder
                      │
                      ├── AgentTypeRegistry
                      │       ├── GeminiAgent
                      │       └── OpenCodeAgent
                      │
                      └── ProjectConfigLoader

IdeBridgeServer ──► McpDispatcher
                     └── tools/list → auto-generated from McpToolRegistry
                     └── tools/call → McpToolRegistry ──► 28 tools (self-describing schemas)
                                                              └── IdeServiceImpl
                                                                    ├── FileService
                                                                    ├── EditorService
                                                                    ├── LspService
                                                                    ├── ProjectService
                                                                    ├── GitService
                                                                    └── TerminalService
```

### New/Reorganized Files Created

| File | Purpose |
|------|---------|
| `ai/session/AgentEnvironmentBuilder.kt` | Consolidated env var construction |
| `ai/agents/AgentTypeRegistry.kt` | Extensible agent registration |
| `ai/AiConfig.kt` | Centralized hard-coded configs |
| `ai/bridge/IdeNotificationSender.kt` | Notification callback interface (moved from service layer) |

### Files Deleted

| File | Reason |
|------|--------|
| `ai/session/GeminiSessionManager.kt` | Dead code, duplicate of AiSessionManager |
| `ai/IdeMcpTools.kt` | Replaced by self-describing McpTool schemas |

---

## Implementation Order

1. ✅ **Phase 1** — Eliminate Duplication & Consolidate
2. ✅ **Phase 4** — Improve Module Boundaries & Interfaces
3. ✅ **Phase 2** — Fix Concurrency & Threading
4. ✅ **Phase 3** — Fix Fragility & Bugs
5. ✅ **Phase 6** — Cleanup & Migration
6. ⏳ **Phase 5** — Add Test Infrastructure (future work)
