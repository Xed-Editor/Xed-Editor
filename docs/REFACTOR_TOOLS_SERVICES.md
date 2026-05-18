# Refactor Plan: MCP Tools & Services Layer

## Current State
- **34 tool classes**, 12 files under `bridge/tools/`
- **6 services** behind a monolithic `IdeService` interface (34 methods)
- **4 of 6 services** coupled to `MainViewModel` (Android ViewModel god-object)
- Massive duplication: argument parsing, validation, error handling, response building
- No centralized error types, no auto-validation from schema, inconsistent patterns

---

## ✅ Phase 1: Tool Layer Foundation (COMPLETE)

### 1a. Create `BaseMcpTool` abstract class ✅
- **File**: `bridge/tools/BaseMcpTool.kt` — created with:
  - Template `execute()` → `validateRequired()` → `executeValidated()`
  - Helpers: `requireString()`, `requireInt()`, `requireBoolean()`, `optionalString()`, `optionalInt()`, `optionalBoolean()`
  - `resolvePathOrThrow()` — typed `PathOutsideWorkspace` error
  - `getTimeoutMs(): Long = 60000L` — per-tool timeout
- Updated all 34 tool classes to extend `BaseMcpTool` instead of `McpTool`

### 1b. Add typed error hierarchy ✅
- **File**: `bridge/tools/ToolError.kt` — sealed class hierarchy:
  - `MissingParam`, `PathOutsideWorkspace`, `InvalidParam`, `ToolTimeout`, `Internal`
  - Each carries structured error code matching JSON-RPC conventions
- `McpDispatcher` now catches `ToolError` first (typed codes), then generic `Exception`

### 1c. Consolidate response building ✅
- All 34 tools now use `textResult()` / `jsonResult()` consistently
- No more inline `JsonObject` content/array construction in tool classes
- `GetDiffResultTool` returns `textResult(content)` instead of double-wrapping

### 1d. Extract shared patch flow (PENDING)
- `WriteFileTool` and `OpenDiffTool` still have near-duplicate patch/show logic
- TBD: extract `showPatchAndApply()` helper in a follow-up pass

---

## Phase 2: Reorganization & Standards

### 2a. Fix `CursorTools.kt` misplacement
- Move `GetGitDiffTool` → `GitTools.kt`
- Move `GetProjectConfigTool` → `SystemTools.kt`

### 2b. Standardize parameter naming
- Convention: `filePath` for file paths, `pattern` for search, `path` for git/workspace
- Update all tool schema descriptions to match

### 2c. Add input size validation
- In `BaseMcpTool.requireString()`: `maxLength` parameter (default safe limits)
- Prevents OOM from AI sending massive content

---

## Phase 3: Service Interface Evolution

### 3a. Extract focused sub-interfaces
- Extract from `IdeService`: `FileOps`, `EditorOps`, `LspOps`, `ProjectOps`, `GitOps`, `TerminalOps`
- Keep monolithic `IdeService` for backward compat
- `IdeServiceImpl` implements all

### 3b. Break `MainViewModel` god-object coupling
- Create narrow interfaces: `TabRepository`, `ScopeProvider`, `EditorManagerFacade`
- Services depend on these instead of `MainViewModel` directly
- `MainViewModel` implements them; test mocks implement them independently

---

## Phase 4: Async & Dispatcher

### 4a. Per-tool timeout
- `getTimeoutMs()` on `McpTool` (default 60000)
- `RunCommandTool` → 120s, `GetSelectionTool` → 5s, etc.
- `McpDispatcher` reads timeout per tool

### 4b. Evaluate async server migration
- NanoHTTPD is synchronous; `runBlocking` blocks thread pool
- Plan for Ktor migration as separate phase after other refactors stabilize

---

## Phase 5: Testing

### 5a. `McpDispatcher` unit tests
- Mock `McpToolRegistry`, test all 5 dispatch methods + error paths

### 5b. Tool unit tests
- All 34 tools testable with mock `IdeService` via `BaseMcpTool.executeValidated()`

### 5c. Service unit tests
- After Phase 3b: `FileService`, `GitService`, `TerminalService` become testable without Android

---

## Progress

| Phase | Status |
|-------|--------|
| 1a + 1b + 1c | ✅ Done — BaseMcpTool, ToolError, response consolidation |
| 1d | ✅ Done — `showPatchAndApply()` helper, WriteFile/OpenDiff simplified |
| 2a | ✅ Done — `GetGitDiffTool` → GitTools.kt, `GetProjectConfigTool` → SystemTools.kt |
| 2b | ❌ Cancelled — parameter renaming would break MCP protocol compatibility |
| 2c | ✅ Done — input size validation via BaseMcpTool.requireString() |
| 3a | ✅ Done — 6 sub-interfaces: `FileOps`, `EditorOps`, `LspOps`, `ProjectOps`, `GitOps`, `TerminalOps`. `IdeService` now extends them all for backward compat. |
| 3b | ✅ Done — `TabRepository`, `ScopeProvider`, `FileOpener` interfaces created. `FileService`, `EditorService`, `LspService` now depend on these instead of `MainViewModel`. `IdeServiceImpl` adapts `MainViewModel` to narrow interfaces. |
| 4a | ✅ Done — per-tool timeout; `RunCommandTool` overrides with 120s |
| 4b | ✅ Done — Added `Semaphore` concurrency limiter (max 8 concurrent tool calls), `serverScope.cancel()` on server stop, custom `ServerSocketFactory` with 10s SO_TIMEOUT, proper coroutine lifecycle management. Full Ktor migration documented as future option. |
| 5 | ⏳ Future — tests |

## Implementation Order (Remaining)

1. ⏳ **Phase 5** — Tests

## New Files Created

| File | Purpose |
|------|---------|
| `service/FileOps.kt` | File operations sub-interface |
| `service/EditorOps.kt` | Editor operations sub-interface |
| `service/LspOps.kt` | LSP operations sub-interface |
| `service/ProjectOps.kt` | Project operations sub-interface |
| `service/GitOps.kt` | Git operations sub-interface |
| `service/TerminalOps.kt` | Terminal operations sub-interface |
| `service/TabRepository.kt` | Narrow interface for tab access |
| `service/ScopeProvider.kt` | Narrow interface for coroutine scope |
| `service/FileOpener.kt` | Narrow interface for opening files in editor |

---

## Dependency Graph After Refactor

```
McpDispatcher
  └── McpToolRegistry
        └── 34 tools (each extends BaseMcpTool)
              │  auto-validate from schema
              │  typed error handling
              │  per-tool timeout
              │  standardized response
              │
              └── IdeService
                    └── IdeServiceImpl
                          ├── FileService ───── TabRepository (narrow)
                          ├── EditorService ─── TabRepository + ScopeProvider
                          ├── LspService ────── TabRepository
                          ├── ProjectService ── TabRepository + ScopeProvider
                          ├── GitService ────── (pure)
                          └── TerminalService ── (almost pure)

New files:
- bridge/tools/BaseMcpTool.kt
- bridge/tools/ToolError.kt
- service/TabRepository.kt, ScopeProvider.kt (Phase 3b)
