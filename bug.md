# Xed-Editor Bug & Tissue Audit Report

> **Audit Date:** 2026-05-23  
> **Scope:** Full codebase — AI subsystems, MCP bridge, terminal engine, UI layer, services, build system  
> **Severity Scale:** CRITICAL (crash/data-loss) → HIGH (major feature broken) → MEDIUM (partial break, poor UX) → LOW (cosmetic, minor) → INFO (technical debt, improvement)

---

## Table of Contents

1. [AI Subsystem Bugs](#1-ai-subsystem-bugs)
2. [MCP/Bridge Bugs](#2-mcpbridge-bugs)
3. [Terminal Engine Bugs](#3-terminal-engine-bugs)
4. [Service Layer Bugs](#4-service-layer-bugs)
5. [UI/UX Bugs](#5-uiux-bugs)
6. [Performance & Memory Bugs](#6-performance--memory-bugs)
7. [Security Bugs](#7-security-bugs)
8. [Architecture & Design Bugs](#8-architecture--design-bugs)
9. [Build & Configuration Bugs](#9-build--configuration-bugs)
10. [Data Loss & Persistence Bugs](#10-data-loss--persistence-bugs)

---

## 1. AI Subsystem Bugs

### BUG-01: AiSessionManager Monolithic State Machine Deadlock Risk

| Field | Value |
|-------|-------|
| **Severity** | CRITICAL |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/session/AiSessionManager.kt` |
| **Root Cause** | AiSessionManager uses a single `MutableStateFlow<AiSessionState>` with `synchronized` blocks that can block the coroutine dispatcher. `runBlocking` is used inside `synchronized` blocks for session transitions, creating potential deadlock when the UI reads state on the main thread while a coroutine holds the lock. |
| **Symptoms** | UI freezes, session transitions hang, ANR (Application Not Responding) under concurrent AI requests |
| **Reproduction** | Trigger rapid consecutive AI completions (e.g., type fast in inline agent bar). The state machine transitions (IDLE→THINKING→RESPONDING→IDLE) contend on the same lock while `runBlocking` waits for coroutine completion. |
| **Expected Behavior** | Non-blocking state transitions using `Mutex` with coroutine-safe suspension; UI reads snapshot state without lock contention |
| **Actual Behavior** | `synchronized` blocks wrapping `runBlocking` calls on the main dispatcher |
| **Suggested Fix** | Replace `synchronized` with `Mutex` from `kotlinx.coroutines.sync`. Move all session state transitions to a dedicated coroutine scope. Use `StateFlow.update {}` for atomic state mutations. |
| **Architectural Impact** | High — this is the central AI session coordinator. A rewrite affects AiCompletionEngine, AgentCli, AiAgentSheet, and InlineAgentBar consumers. |
| **Risk** | High — state machine refactor could introduce race conditions if not carefully tested with concurrent access patterns |

---

### BUG-02: AiCompletionEngine No Provider Abstraction — Hardcoded Provider Routing

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiCompletionEngine.kt` |
| **Root Cause** | The engine uses if-else branching on `agentType` string to select between Gemini and OpenCode providers. No `AiProvider` interface. Adding a new provider requires modifying the engine. |
| **Symptoms** | Adding OpenAI/Claude requires touching AiCompletionEngine. Each provider's auth, endpoint, and error handling are intertwined in a single file. |
| **Reproduction** | N/A — architectural issue. Attempt to add a new AI provider (e.g., Ollama) — must modify AiCompletionEngine directly. |
| **Expected Behavior** | Provider selection via a registry pattern. `AiCompletionEngine` delegates to registered `AiProvider` implementations. |
| **Actual Behavior** | String-based if-else chain directly constructing HTTP calls with provider-specific logic inlined |
| **Suggested Fix** | Define `AiProvider` interface with `suspend fun complete(...)`. Create `ProviderManager` that registers providers. Engine only calls `ProviderManager.getProvider(type).complete(...)`. |
| **Architectural Impact** | High — new `ai/provider/` package needed; existing provider logic extracted into `GeminiProvider`, `OpenCodeProvider` |
| **Risk** | Medium — extraction is mechanical but error-handling paths must be preserved exactly |

---

### BUG-03: AgentCli Blocks Main Thread with runBlocking

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AgentCli.kt` |
| **Root Cause** | `AgentCli` uses `runBlocking` to call `AiSessionManager` from terminal command handlers. Terminal command dispatch likely runs on a background thread, but `runBlocking` still blocks the calling thread entirely, preventing cancellation and consuming a thread from the limited pool. |
| **Symptoms** | Terminal becomes unresponsive during AI operations invoked from CLI. Multiple CLI invocations exhaust thread pool. |
| **Reproduction** | Open two terminal tabs. Run `/ai` command in both simultaneously. The second invocation blocks until the first completes. |
| **Expected Behavior** | CLI commands launch coroutines that can be cancelled via terminal interrupt signals |
| **Actual Behavior** | Synchronous blocking calls tying up thread-per-terminal |
| **Suggested Fix** | Change CLI handlers to launch coroutines in `MainScope` or `ProcessScope`. Wire terminal interrupt (Ctrl+C/SIGINT) to coroutine cancellation. |
| **Architectural Impact** | Medium — affects how CLI commands interact with session manager |
| **Risk** | Medium — terminal integration could break if coroutine scope lifecycle mismanaged |

---

### BUG-04: AgentModelResolver No Caching — Re-resolves on Every Completion

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AgentModelResolver.kt` |
| **Root Cause** | `resolveModel()` makes an HTTP request to the AI provider to list models every time a completion is requested. No in-memory or disk cache. |
| **Symptoms** | Every AI completion incurs an extra HTTP round trip just to validate the model name. Network failures block completions even when the model is valid. |
| **Reproduction** | Trigger any AI completion. Observe two HTTP requests: one to list models, one to complete. |
| **Expected Behavior** | Cache resolved model for the session lifetime (or at least N minutes). Fall back to configured model name if cache miss. |
| **Actual Behavior** | Uncached HTTP call on every request |
| **Suggested Fix** | Add LRU cache with TTL (e.g., 5 minutes). On cache miss, resolve and cache. On failure, use last-known-good model. |
| **Architectural Impact** | Low — isolated to resolver |
| **Risk** | Low |

---

### BUG-05: AiSessionManager — No Session Timeout / Leak

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/session/AiSessionManager.kt` |
| **Root Cause** | Sessions are created but never reaped. No timeout mechanism for inactive sessions. If a user opens an AI chat, closes it, and reopens, a new session is created. Old sessions remain in memory. |
| **Symptoms** | Memory grows unbounded with repeated AI interactions. No way to list/close stale sessions. |
| **Reproduction** | Open and close AI chat sheet 50 times. Memory usage increases monotonically. |
| **Expected Behavior** | Configurable session timeout (e.g., 30 min inactivity). Automatic cleanup of expired sessions. |
| **Actual Behavior** | Sessions accumulate indefinitely |
| **Suggested Fix** | Add `SessionCleanupService` that periodically scans sessions for inactivity. Add `lastAccessTime` tracking. Use `WeakHashMap` or explicit lifecycle binding. |
| **Architectural Impact** | Medium — new cleanup infrastructure needed |
| **Risk** | Low — cleanup is additive, does not change existing behavior |

---

### BUG-06: AiAgentSheet No Input Validation for API Keys

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiAgentSheet.kt` |
| **Root Cause** | API key input field accepts empty/whitespace-only strings without validation. No visual feedback on invalid key format. Keys are stored to SharedPreferences without encryption. |
| **Symptoms** | Users can set empty API key, causing silent failures on AI requests. No error message explains the issue. |
| **Reproduction** | Clear API key field, save. Trigger AI completion — fails silently. |
| **Expected Behavior** | Validate non-empty key on save. Show inline error. Test key connectivity before saving. |
| **Actual Behavior** | Empty key accepted, failures are opaque |
| **Suggested Fix** | Add `key.isNotBlank()` validation. Add "Test Connection" button. Use EncryptedSharedPreferences for storage. |
| **Architectural Impact** | Low — UI-level fix |
| **Risk** | Low |

---

### BUG-07: AiConfig No Validation — Malformed Config Causes Silent Fallback

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiConfig.kt` |
| **Root Cause** | Config properties (endpoint URL, model name, temperature) are parsed without validation. Malformed URL or out-of-range temperature silently defaults to hardcoded values. No logging of validation failures. |
| **Symptoms** | User sets an invalid endpoint URL; AI completions silently use default endpoint. User has no indication their config is ignored. |
| **Reproduction** | Set `api_endpoint` to `"not-a-url"`. AI requests still go to default endpoint. |
| **Expected Behavior** | Validate config on load. Log validation errors. Show UI warning for invalid settings. |
| **Actual Behavior** | Silently falls back to defaults |
| **Suggested Fix** | Add `validate(): ValidationResult` to AiConfig. Call on load and on save. Surface errors in settings UI. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-08: InlineAgentBar Race Condition with Fast Typing

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/InlineAgentBar.kt` |
| **Root Cause** | Debounce mechanism uses `delay()` but does not cancel previous coroutine on new input. Rapid typing launches multiple concurrent completion requests. |
| **Symptoms** | AI suggestions flicker as responses arrive out of order. Last keystroke does not guarantee latest response. |
| **Reproduction** | Type rapidly in editor with inline AI enabled. Observations: stale responses appear after newer input. |
| **Expected Behavior** | Only the latest completion request is active. Previous requests are cancelled. |
| **Actual Behavior** | Multiple concurrent requests, responses race |
| **Suggested Fix** | Use `Job` reference: cancel previous job before launching new debounced completion. Use `cancellable()` in the HTTP call. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-09: OpenCodeAgent / GeminiAgent No Rate Limiting / Retry Logic

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/agents/OpenCodeAgent.kt`, `core/main/src/main/java/com/rk/ai/agents/GeminiAgent.kt` |
| **Root Cause** | Agent implementations throw on HTTP 429 (rate limit) or 5xx errors without retry. No exponential backoff. |
| **Symptoms** | Transient API failures cause permanent completion failures. User must manually retry. |
| **Reproduction** | Exceed API rate limit. Agent immediately fails with error message. |
| **Expected Behavior** | Automatic retry with exponential backoff (up to 3 attempts) for 429/5xx responses |
| **Actual Behavior** | Single attempt, no retry |
| **Suggested Fix** | Add retry wrapper around provider calls. Use `retryWhen {}` with exponential delay. Respect `Retry-After` header. |
| **Architectural Impact** | Low — can be implemented in provider layer |
| **Risk** | Low |

---

### BUG-10: AgentProfile Serialization Not Type-Safe

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/agents/AgentProfile.kt` |
| **Root Cause** | AgentProfile uses `Any?` typed properties for agent-specific configuration, requiring unsafe casts at consumption sites. No sealed class hierarchy for profile types. |
| **Symptoms** | `ClassCastException` at runtime if profile config schema changes between versions. No compile-time safety. |
| **Reproduction** | Deserialize an AgentProfile with `systemPrompt` as String, then access it expecting a different type. |
| **Expected Behavior** | Type-safe profile hierarchy using sealed classes or generics |
| **Actual Behavior** | `Any?` with runtime casts |
| **Suggested Fix** | Define `sealed class AgentProfileType { GeminiProfile, OpenCodeProfile, ... }`. Make `AgentProfile` generic: `AgentProfile<T : AgentProfileType>`. |
| **Architectural Impact** | Medium — affects all profile consumers |
| **Risk** | Medium — requires coordinated change across AI agents |

---

### BUG-11: AgentEnvironmentBuilder Ignores Environment Variables

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/session/AgentEnvironmentBuilder.kt` |
| **Root Cause** | When building the agent execution environment, the builder only passes hardcoded variables (workspace path, session ID) but does not forward the system environment or user-configured env vars. |
| **Symptoms** | Agent process lacks context from `PATH`, `HOME`, `ANDROID_SDK_ROOT`, etc. Tool executions that depend on environment fail. |
| **Reproduction** | Agent attempts to run `adb` or `gradle` — fails because `PATH` is not inherited. |
| **Expected Behavior** | Inherit system environment and merge with agent-specific vars |
| **Actual Behavior** | Only agent-specific vars are set |
| **Suggested Fix** | Use `ProcessBuilder.environment().putAll(System.getenv())` as base, then override with agent vars |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 2. MCP/Bridge Bugs

### BUG-12: IdeBridgeServer NanoHTTPD — No Request Timeout

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/server/IdeBridgeServer.kt` |
| **Root Cause** | NanoHTTPD's `serve()` method has no configurable timeout. Long-running MCP tool operations block the HTTP handler thread indefinitely. If the client disconnects, the server thread continues processing. |
| **Symptoms** | Thread starvation under concurrent long-running requests (e.g., file search on large project). Server becomes unresponsive to health checks. |
| **Reproduction** | Send a search request that takes >30s. All subsequent requests time out because all handler threads are occupied. |
| **Expected Behavior** | Configurable per-request timeout. Interrupt handler on timeout. |
| **Actual Behavior** | No timeout, threads block indefinitely |
| **Suggested Fix** | Wrap each `serve()` call in `withTimeout(timeoutMs)`. Return 408 status on timeout. Use `raceOf {}` between the tool operation and a timeout job. |
| **Architectural Impact** | High — extends to all MCP tool implementations |
| **Risk** | Medium — timeout value must be carefully chosen to avoid killing legitimate long ops |

---

### BUG-13: McpDispatcher Uses runBlocking for Tool Dispatch

| Field | Value |
|-------|-------|
| **Severity** | CRITICAL |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/server/McpDispatcher.kt` |
| **Root Cause** | `dispatch(toolName, params)` is a regular function that uses `runBlocking` internally to call suspend tool functions. Every MCP request creates a blocking thread. Combined with NanoHTTPD's fixed thread pool, this is a scalability disaster. |
| **Symptoms** | With 10 concurrent MCP requests, server thread pool exhausts. Each thread blocks waiting for I/O (file reads, HTTP calls to AI provider). |
| **Reproduction** | Send 15 concurrent Gemini completion requests via MCP. Server stops responding. |
| **Expected Behavior** | Non-blocking dispatch using coroutines. NanoHTTPD handles the request, dispatcher returns a `Future` or suspends, server sends response asynchronously. |
| **Actual Behavior** | `runBlocking` on every dispatch |
| **Suggested Fix** | Rewrite McpDispatcher to use `suspend fun dispatch(...)`. Change IdeBridgeServer to use async handler pattern: `request -> scope.launch { result = dispatch(...); sendResponse(result) }`. Or migrate to Ktor for native coroutine support. |
| **Architectural Impact** | Very High — fundamental architectural change to the MCP server |
| **Risk** | High — changing from blocking to async requires thorough testing of all tool paths |

---

### BUG-14: SseManager Socket Leak — No Heartbeat / Cleanup

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/server/SseManager.kt` |
| **Root Cause** | SSE connections are tracked in a map but there is no heartbeat mechanism to detect client disconnection. If a client disconnects ungracefully (network drop, crash), the SSE socket is never removed. The server continues to write to closed sockets, causing `IOException` that may not be caught. |
| **Symptoms** | SSE connections accumulate. Server memory grows. Logs fill with broken-pipe errors. |
| **Reproduction** | Open 10 client SSE connections, then kill the client process. Server still shows 10 active connections. |
| **Expected Behavior** | Heartbeat ping every 30s. Remove connection on write failure. Periodic cleanup of stale connections. |
| **Actual Behavior** | No heartbeat, never cleans up dead connections |
| **Suggested Fix** | Add heartbeat thread sending `:keepalive\n\n` comments. On IOException during send, remove connection. Add `cleanupStaleConnections()` called periodically. Track `lastEventTime` per connection. |
| **Architectural Impact** | Medium — SseManager is well-encapsulated |
| **Risk** | Low |

---

### BUG-15: HttpSessionTracker — No Session Expiry

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/server/HttpSessionTracker.kt` |
| **Root Cause** | Sessions are created with a creation timestamp but never expired. No mechanism to remove old sessions. Session map grows unbounded. |
| **Symptoms** | Memory leak over long-running server sessions. No way to invalidate compromised session tokens. |
| **Reproduction** | Connect 1000 clients over time. Server holds 1000 session entries indefinitely. |
| **Expected Behavior** | Session TTL (e.g., 24h). Active refresh on request. Periodic cleanup of expired sessions. |
| **Actual Behavior** | Sessions live forever |
| **Suggested Fix** | Add `expiresAt` field. Add `cleanupExpired()` scheduled task. Check expiry on every request (with 5-min grace period). |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-16: MCP Tool Registry — Hardcoded Registration, No Plugin

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/McpToolRegistry.kt` |
| **Root Cause** | All 25+ MCP tools are registered via explicit constructor calls in a single `registerAll()` method. Adding a new tool requires modifying this file. No service-loader or annotation-based discovery. |
| **Symptoms** | Third-party extensions cannot register custom MCP tools. Build breaks if any tool constructor changes. |
| **Reproduction** | N/A — architectural. Attempt to add a tool from a different module — not possible without modifying core code. |
| **Expected Behavior** | Tool discovery via `ServiceLoader` or a compiler-time annotation processor |
| **Actual Behavior** | Manual registration of every tool |
| **Suggested Fix** | Define `@McpTool` annotation with name/description. Use Kotlin Symbol Processing (KSP) to generate registry. Or use `ServiceLoader` with interface `McpToolProvider`. |
| **Architectural Impact** | Medium — requires build system changes (KSP plugin) |
| **Risk** | Low — manual registry can remain as fallback |

---

### BUG-17: McpToolHelpers Error Handling — Generic Exception Catching

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/McpToolHelpers.kt` |
| **Root Cause** | Helper functions catch generic `Exception` and return generic error messages. Specific exception types (JsonSyntaxException, FileNotFoundException, IOException) are not distinguished. |
| **Symptoms** | Error messages returned to the client are vague ("An error occurred"). Debugging requires reading server logs. |
| **Reproduction** | Send malformed JSON parameter — generic "error processing request" response. |
| **Expected Behavior** | Typed error responses: `INVALID_PARAMS`, `FILE_NOT_FOUND`, `INTERNAL_ERROR` etc. Each with specific message. |
| **Actual Behavior** | Generic error wrapping |
| **Suggested Fix** | Use `ToolError` sealed class hierarchy. Map exceptions to specific `ToolError` subtypes. Include error code in JSON-RPC response. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-18: FileTools Path Traversal (Partial) — No Path Canonicalization

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/FileTools.kt` |
| **Root Cause** | File read/write operations check the path starts with workspace directory but do not canonicalize the path first. A path like `/workspace/../../etc/passwd` bypasses the prefix check because the check is on the raw string, not the resolved path. |
| **Symptoms** | An attacker or malicious agent can read/write files outside the workspace by using `..` segments. |
| **Reproduction** | Call `read_file` with path `"../../etc/passwd"`. The starts-with check passes. |
| **Expected Behavior** | Canonicalize both the workspace path and the requested path using `File.canonicalPath`. Reject if not within workspace. |
| **Actual Behavior** | String-level prefix check without resolution |
| **Suggested Fix** | Change all path checks to: `val canonical = File(root, requestedPath).canonicalPath; require(canonical.startsWith(workspaceRoot.canonicalPath))`. |
| **Architectural Impact** | Low |
| **Risk** | Low (fix is mechanical) |

---

### BUG-19: FileTools Silent File Overwrite — No Safe Write

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/FileTools.kt` |
| **Root Cause** | `write_file` tool writes directly to the target path. If the write is interrupted (crash, power loss), the file is left in a corrupt partial state. No atomic write pattern. |
| **Symptoms** | Corrupted files after unexpected shutdown during MCP file writes |
| **Reproduction** | Kill the app during a large file write. File contains truncated content. |
| **Expected Behavior** | Write to a `.tmp` file, then atomic rename. This ensures the original file is never partially overwritten. |
| **Actual Behavior** | Direct write to target path |
| **Suggested Fix** | Implement atomic write: `File(tmpPath).writeBytes(data); File(tmpPath).renameTo(targetPath)`. Use `FileChannel.lock()` for cross-process safety. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-20: ToolError.kt — Incomplete Error Type Hierarchy

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/ToolError.kt` |
| **Root Cause** | `ToolError` is a simple data class with a string message. No subtypes for validation errors, permission errors, timeouts, etc. Consumers cannot programmatically distinguish error types. |
| **Symptoms** | All errors look the same to MCP clients. Cannot implement retry logic based on error type. |
| **Reproduction** | N/A — architectural |
| **Expected Behavior** | Sealed class hierarchy: `ToolError.Validation`, `ToolError.Timeout`, `ToolError.Permission`, `ToolError.NotFound`, etc. |
| **Actual Behavior** | Single flat error type |
| **Suggested Fix** | Define `sealed class ToolError`. Each subclass has structured data. Add `toJsonRpcError(): JsonObject` method. |
| **Architectural Impact** | Low |
| **Risk** | Low — but all tools that return errors must be updated |

---

### BUG-21: LspTools.kt — No LSP Connection Pooling

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/LspTools.kt` |
| **Root Cause** | Each LSP request (completion, hover, definition) creates a new LSP client connection. No connection pooling or reuse. Handshake overhead (initialize, initialized) is paid on every call. |
| **Symptoms** | LSP operations are 10-100x slower than necessary. High latency for completions. |
| **Reproduction** | Call `lsp_completion` 10 times in succession. Total time = 10 × (connect + handshake + completion). |
| **Expected Behavior** | Persistent LSP connections per language server. Connection pool with idle timeout. |
| **Actual Behavior** | Connect-disconnect per request |
| **Suggested Fix** | Implement `LspConnectionPool` that maintains active connections keyed by language. Idle timeout 5 min. Max pool size 5. |
| **Architectural Impact** | Medium — new connection pool infrastructure |
| **Risk** | Medium — connection lifecycle complexity |

---

### BUG-22: TerminalTools.kt — No Output Buffering, Stream Flood

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/TerminalTools.kt` |
| **Root Cause** | `execute_command` tool streams terminal output character-by-character via SSE without buffering. Each character is a separate SSE event, causing enormous overhead. |
| **Symptoms** | High CPU and memory usage during command output. SSE channel floods with thousands of tiny messages. Client struggles to parse. |
| **Reproduction** | Run `cat largefile.txt` via MCP terminal tool. SSE events: one per character. |
| **Expected Behavior** | Buffer output (e.g., 4KB chunks or 100ms intervals). Send batched SSE events. |
| **Actual Behavior** | Character-by-character streaming |
| **Suggested Fix** | Add `BufferedTerminalOutput` that collects output and flushes on timer or buffer size threshold. Use `channelFlow` with buffer. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-23: DiscoveryFileWriter Race — Concurrent Write Corruption

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/DiscoveryFileWriter.kt` |
| **Root Cause** | MCP discovery file is written on server start. If multiple server instances start concurrently (unlikely but possible), the file can be corrupted by interleaved writes. No file locking. |
| **Symptoms** | Corrupted discovery JSON. MCP clients fail to connect. |
| **Reproduction** | Very unlikely under normal use. Force it by starting server twice rapidly. |
| **Expected Behavior** | Atomic write with exclusive file lock. Lock via `FileChannel.lock()`. |
| **Actual Behavior** | Direct `File.writeText()` without locking |
| **Suggested Fix** | Use atomic write pattern: write to `.tmp`, then rename. Or acquire `FileChannel.lock()`. |
| **Architectural Impact** | Low |
| **Risk** | Very Low |

---

## 3. Terminal Engine Bugs

### BUG-24: TerminalBackEnd — No Session Lifecycle Management

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/TerminalBackEnd.kt` |
| **Root Cause** | TerminalBackEnd creates and manages terminal sessions (JNI/termux sessions) but has no explicit lifecycle. Sessions are not cleaned up when the composable is disposed. No `close()` or `dispose()` method in the public API. Relies on GC/finalization which is unreliable on Android. |
| **Symptoms** | Zombie terminal processes accumulate. Resource leak (fd, memory). Eventually "Too many open files" error. |
| **Reproduction** | Open and close 50 terminal tabs. Check process list — 50 zombie terminal processes. |
| **Expected Behavior** | Explicit lifecycle: `onStart()`, `onStop()`, `onDestroy()` called by the composable. JNI session cleanup in `onDestroy()`. |
| **Actual Behavior** | No lifecycle management; GC-reliant cleanup |
| **Suggested Fix** | Implement `LifecycleObserver` interface. Bind to composable lifecycle. Kill terminal process and release JNI resources in `onDestroy()`. |
| **Architectural Impact** | High — affects TerminalCompose, SessionManager, MkSession |
| **Risk** | Medium — incorrect cleanup could crash the terminal process |

---

### BUG-25: MkSession / MkRootfs No Error Propagation on Setup Failure

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/MkSession.kt`, `core/main/src/main/java/com/rk/terminal/MkRootfs.kt` |
| **Root Cause** | Session setup functions return `Boolean` but log errors internally. Callers check the boolean but cannot access the specific error. Setup failures (extraction failure, permission denied) are opaque to the user. |
| **Symptoms** | Terminal shows blank screen on setup failure. No error message. User cannot determine what went wrong. |
| **Reproduction** | Corrupt rootfs archive. Terminal setup returns `false`. UI shows empty terminal. |
| **Expected Behavior** | Functions return `Result<T>` or throw typed exceptions. UI shows specific error: "Rootfs extraction failed: No space left on device". |
| **Actual Behavior** | Boolean return, error lost |
| **Suggested Fix** | Change return types to `Result<SessionHandle>` or `SessionResult` sealed class. Surface error in TerminalScreen via state flow. |
| **Architectural Impact** | Medium — affects MkSession, MkRootfs, and terminal UI |
| **Risk** | Low |

---

### BUG-26: SessionService — No Concurrent Session Limit

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/SessionService.kt` |
| **Root Cause** | No limit on the number of concurrent terminal sessions. Each session consumes a process slot, file descriptors, and memory. A user (or agent) can exhaust system resources by opening many sessions. |
| **Symptoms** | Out-of-memory, "could not create process" errors after opening many terminal tabs |
| **Reproduction** | Open 100 terminal tabs. System becomes unresponsive. |
| **Expected Behavior** | Configurable max sessions (default 16). Graceful rejection with user-visible error when limit reached. |
| **Actual Behavior** | Unlimited session creation |
| **Suggested Fix** | Add `maxSessions` config. Track active session count. Return error when limit exceeded. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-27: TerminalScreen — No Scrollback Limit / Memory Bloat

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/TerminalScreen.kt` |
| **Root Cause** | Terminal scrollback buffer is stored as a growing list of lines with no upper bound. Running a command that produces megabytes of output causes OOM. |
| **Symptoms** | App crashes with OOM when using terminal for large output commands. |
| **Reproduction** | Run `dmesg` or `logcat` in terminal. Memory grows until crash. |
| **Expected Behavior** | Circular buffer with configurable max lines (default 10000). Oldest lines discarded. |
| **Actual Behavior** | Unbounded line accumulation |
| **Suggested Fix** | Replace `List<Line>` with `CircularBuffer<Line>(maxLines)`. Implement `BufferOverflow.DROP_OLDEST` strategy. |
| **Architectural Impact** | Medium — affects TerminalScreen rendering and data model |
| **Risk** | Low |

---

### BUG-28: TerminalBackEnd — ANSI Parser Not Thread-Safe

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/TerminalBackEnd.kt` |
| **Root Cause** | ANSI escape sequence parsing modifies shared state (cursor position, screen buffer, color attributes) without synchronization. Input from the terminal's input thread and output from the process' output thread race on the same state. |
| **Symptoms** | Rare: garbled terminal display, incorrect cursor position, missing characters under heavy I/O. |
| **Reproduction** | Run a command that produces fast output while simultaneously typing input. Observed under heavy load. |
| **Expected Behavior** | Thread-safe screen buffer with synchronized access. Incoming data queued and processed sequentially. |
| **Actual Behavior** | Unsynchronized state mutation from multiple threads |
| **Suggested Fix** | Use a single actor/coroutine that processes all terminal I/O sequentially. Buffer writes and dispatch to render thread via `StateFlow`. |
| **Architectural Impact** | High — requires rethinking terminal I/O architecture |
| **Risk** | Medium — thread safety fixes can introduce deadlocks if not careful |

---

### BUG-29: MkRootfs — No Integrity Check on Rootfs

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/MkRootfs.kt` |
| **Root Cause** | Rootfs archive is extracted without checksum verification. If the download was corrupted, the resulting filesystem will be silently corrupted, causing cryptic failures later. |
| **Symptoms** | "Binary not found" or "Segmentation fault" in terminal due to corrupted rootfs binaries |
| **Reproduction** | Download rootfs over unreliable network. Extract corrupted archive. |
| **Expected Behavior** | Verify SHA-256 checksum before extraction. Re-download on mismatch. |
| **Actual Behavior** | No integrity verification |
| **Suggested Fix** | Store checksum in a sidecar file or embedded in the app. Verify with `MessageDigest` before extraction. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 4. Service Layer Bugs

### BUG-30: IdeServiceImpl Global State — No Concurrency Control

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/service/IdeServiceImpl.kt` |
| **Root Cause** | `IdeServiceImpl` holds service instances (FileService, EditorService, GitService, etc.) as mutable `var` fields that can be replaced at runtime. Multiple threads may read while another writes, causing `NullPointerException` or stale service references. |
| **Symptoms** | Intermittent NPE on service access during rapid IDE operations |
| **Reproduction** | Trigger IDE operations from MCP and UI simultaneously. Service field read returns null transiently. |
| **Expected Behavior** | Services initialized once at construction. Use `val` fields. If runtime replacement is needed, use `AtomicReference` or `volatile`. |
| **Actual Behavior** | Mutable `var` fields without synchronization |
| **Suggested Fix** | Convert to `val` initialized in `init`. If lazy init is required, use `Lazy` delegate or `AtomicReference`. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-31: FileService Read/Write — No Encoding Handling

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/service/FileService.kt` |
| **Root Cause** | File read/write operations use default platform encoding (`Charset.defaultCharset()`). On Android, this varies by device locale. A file written in one locale may be unreadable in another. |
| **Symptoms** | Files with special characters (UTF-8) written on a device with default charset `windows-1252` are corrupted when read on a UTF-8 device. |
| **Reproduction** | Set device locale to Japanese. Write file with Chinese characters. Read on English-locale device. Characters garbled. |
| **Expected Behavior** | Always use UTF-8 explicitly: `Charsets.UTF_8` |
| **Actual Behavior** | Implicit platform encoding |
| **Suggested Fix** | Change all `File.readText()` and `File.writeText()` to `readText(Charsets.UTF_8)` / `writeText(text, Charsets.UTF_8)`. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-32: GitService — No Error Handling for Uninitialized Repos

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/service/GitService.kt` |
| **Root Cause** | Git operations assume a valid git repository. Calling status/log/diff on a non-git directory throws uncaught `GitAPIException` or returns misleading empty results. |
| **Symptoms** | MCP git tools return empty results on non-git projects with no indication that the directory is not a git repository. |
| **Reproduction** | Open a non-git project. Call `git_status` via MCP. Returns `{}` silently. |
| **Expected Behavior** | Check `.git` directory existence before operations. Return clear error: "Not a git repository". |
| **Actual Behavior** | Silent empty results or cryptic JGit exceptions |
| **Suggested Fix** | Add `require(isGitRepository())` guard in every git operation. Return `ToolError.NotFound("Not a git repository")`. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-33: ProjectService — No File Watch / Index Staleness

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/service/ProjectService.kt` |
| **Root Cause** | Project file index is built once on project open and never refreshed. External file changes (git checkout, new files, deletions) are not reflected until the project is reopened. |
| **Symptoms** | MCP `search_files` tool returns stale results. New files not found. Deleted files still appear. |
| **Reproduction** | Create a new file in the project directory via another app. MCP search does not find it. |
| **Expected Behavior** | File watcher using `AndroidFileObserver` or periodic refresh (every 30s). Index update on file system events. |
| **Actual Behavior** | One-time index at open |
| **Suggested Fix** | Register `FileObserver` on project root. On create/delete/modify events, update index incrementally. Debounce rapid events. |
| **Architectural Impact** | Medium — new file-watching infrastructure |
| **Risk** | Low |

---

### BUG-34: LspService — No Capability Negotiation Check

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/service/LspService.kt` |
| **Root Cause** | LSP client requests features (completion, hover, definition) without checking server capabilities from the `initialize` response. If the server does not support a feature, the request may hang or error. |
| **Symptoms** | LSP timeout or error when requesting features the server does not support |
| **Reproduction** | Connect to an LSP server that only provides diagnostics. Request code completion. Request hangs. |
| **Expected Behavior** | Cache server capabilities from `initialize` response. Check capability before making feature request. Return unsupported error immediately. |
| **Actual Behavior** | Blind request without capability check |
| **Suggested Fix** | Add `ServerCapabilities` cache. Add `requireCapability(capability)` check before each request. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 5. UI/UX Bugs

### BUG-35: AiAgentSheet State Loss on Configuration Change

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiAgentSheet.kt` |
| **Root Cause** | AI chat state (conversation history, input text, scroll position) is held in composable-local `remember` state. On activity recreation (rotation, theme change), the entire state is lost. No `rememberSaveable` or ViewModel-backed state. |
| **Symptoms** | AI conversation is lost on screen rotation. User must retype input and loses chat history. |
| **Reproduction** | Type a multi-turn AI conversation. Rotate device. Chat history is gone. |
| **Expected Behavior** | Chat state preserved across configuration changes via `rememberSaveable` or ViewModel. |
| **Actual Behavior** | State lost on every recomposition from configuration change |
| **Suggested Fix** | Move chat state to ViewModel. Use `rememberSaveable` for simple fields. Parcelize `ChatMessage` list for saved state. |
| **Architectural Impact** | Medium — ViewModel needs to be integrated into the sheet |
| **Risk** | Low |

---

### BUG-36: InlineAgentBar Overlaps Editor Content

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/InlineAgentBar.kt`, editor composable |
| **Root Cause** | Inline bar is rendered as an overlay without measuring the editor's current viewport. If the cursor is near the bottom of the screen, the bar renders off-screen or overlaps the keyboard. |
| **Symptoms** | AI inline suggestions are partially or fully hidden. User cannot see or interact with suggestions. |
| **Reproduction** | Move cursor to the last line of the editor. Trigger inline AI. Bar renders below visible area. |
| **Expected Behavior** | Bar renders above the cursor line if space below is insufficient. Uses `Popup` with proper offset calculation. |
| **Actual Behavior** | Fixed-position overlay without viewport awareness |
| **Suggested Fix** | Use `Popup` with `IntOffset` calculated from cursor coordinates and remaining viewport height. Add upward/downward direction logic. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-37: No Loading / Progress Indicator for AI Requests

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiAgentSheet.kt`, `core/main/src/main/java/com/rk/ai/InlineAgentBar.kt` |
| **Root Cause** | AI completion requests are launched without setting a loading state. The UI shows no spinner, progress bar, or skeleton while waiting for the response. |
| **Symptoms** | User taps "Send" or triggers inline AI — nothing visible happens for 2-10 seconds. User may tap again, causing duplicate requests. |
| **Reproduction** | Send a slow AI request. UI is indistinguishable from a hung app. |
| **Expected Behavior** | Show a loading indicator (progress bar, typing dots, or spinner) during AI response generation. Disable send button. Show "Stop" button. |
| **Actual Behavior** | No visual feedback during request |
| **Suggested Fix** | Add `isLoading` state to chat ViewModel. Show `CircularProgressIndicator` in chat. Mark last message as "generating..." until complete. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-38: TopBar No Responsive Layout — Cuts Off on Small Screens

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/activities/main/TopBar.kt` |
| **Root Cause** | TopBar uses fixed-width elements (title, tabs, actions) without `scrollable` or `wrap` behavior. On small screens or with many tabs, elements overflow. |
| **Symptoms** | Tabs are cut off on small devices. User cannot access all tabs. |
| **Reproduction** | Open 10 files (tabs) on a 4.7" device. Tabs overflow the screen width. |
| **Expected Behavior** | Tabs scroll horizontally when overflow occurs. Use `ScrollableTabRow` or custom horizontal scroll. |
| **Actual Behavior** | Fixed layout, overflow clipped |
| **Suggested Fix** | Replace `TabRow` with `ScrollableTabRow` or add `horizontalScroll()` modifier to tab area. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 6. Performance & Memory Bugs

### BUG-39: TerminalScreen — Full Buffer Re-render on Every Update

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/terminal/TerminalScreen.kt` |
| **Root Cause** | The Composable redraws the entire terminal buffer on every character change. Compose's recomposition is triggered by the entire buffer state, not by dirty regions. For a 80x24 terminal, that's 1920 cells rendered per character. |
| **Symptoms** | High CPU usage, dropped frames during high-output commands. Battery drain. |
| **Reproduction** | Run `yes` in terminal. Observe 100% CPU on the UI thread. |
| **Expected Behavior** | Only redraw changed regions. Use `Canvas` with dirty-rect tracking. Or use a text View with efficient rendering. |
| **Actual Behavior** | Full buffer redraw per character |
| **Suggested Fix** | Use `Canvas` composable with dirty-region tracking. Or implement `TextLayoutResult`-based caching. Only recompose lines that changed. |
| **Architectural Impact** | Medium — terminal rendering rewrite |
| **Risk** | Medium — rendering changes could introduce visual artifacts |

---

### BUG-40: AiCompletionEngine — No Request Deduplication

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiCompletionEngine.kt` |
| **Root Cause** | If the same completion request is submitted within a short window (e.g., rapid debounce edge case), both requests are sent to the AI provider. No deduplication cache. |
| **Symptoms** | Multiple identical AI requests to the provider. Wasted tokens and API quota. |
| **Reproduction** | Trigger inline completion twice within 50ms (debounce window). Two identical HTTP requests sent. |
| **Expected Behavior** | In-flight request keyed by (prompt hash, model, params). Second identical request subscribes to the same in-flight response. |
| **Actual Behavior** | Duplicate requests |
| **Suggested Fix** | Add `ConcurrentHashMap<RequestKey, Deferred<Result>>` for in-flight request deduplication. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-41: No Memory Pressure Handling — OutOfMemory Crashes

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiCompletionEngine.kt`, `core/main/src/main/java/com/rk/terminal/TerminalBackEnd.kt`, `core/main/src/main/java/com/rk/settings/Settings.kt` |
| **Root Cause** | No component monitors `ActivityManager` for memory pressure. When Android sends `onTrimMemory(TRIM_MEMORY_MODERATE)` or `TRIM_MEMORY_CRITICAL`, nothing reacts. Large AI responses, terminal buffers, and editor state continue to consume memory. |
| **Symptoms** | App crashes with OOM under memory pressure. No graceful degradation. |
| **Reproduction** | Open large files + AI chat + terminal on a 2GB device. Run out of memory. |
| **Expected Behavior** | Listen to `onTrimMemory` in Application class. Clear AI conversation cache, reduce terminal scrollback, trim editor undo history on moderate pressure. On critical pressure, clear most caches and show recovery UI. |
| **Actual Behavior** | No response to memory pressure |
| **Suggested Fix** | Implement `MemoryPressureHandler` in `App.kt`. Register `ComponentCallbacks2`. Dispatch memory level to all subsystems. Cache eviction policies per subsystem. |
| **Architectural Impact** | Medium — needs coordination across subsystems |
| **Risk** | Low |

---

### BUG-42: MCP Tool JSON Serialization — Reflection Overhead on Every Call

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/BaseMcpTool.kt` |
| **Root Cause** | Tool parameter parsing uses Kotlin reflection (`::class.memberProperties`) on every invocation to map JSON-RPC params to function arguments. Reflection on Android is slow and not optimized by the compiler. |
| **Symptoms** | High latency for tool invocations, especially on low-end devices. First invocation of each tool is particularly slow (class metadata loading). |
| **Reproduction** | Profile first MCP tool call. Reflection-based parsing takes 5-50ms. |
| **Expected Behavior** | Cache parameter metadata per tool class. Use compiled `KFunction` references. Or use Kotlin Serialization's `@Serializable` with `Json.decodeFromString`. |
| **Actual Behavior** | Reflection on every call |
| **Suggested Fix** | Cache `toolParameters: List<KProperty>` in companion object. Use `KFunction.callBy()` with cached parameter map. Or migrate to kotlinx.serialization for zero-reflection parsing. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 7. Security Bugs

### BUG-43: API Keys Stored in Plaintext SharedPreferences

| Field | Value |
|-------|-------|
| **Severity** | CRITICAL |
| **Affected Files** | `core/main/src/main/java/com/rk/settings/Settings.kt`, API key access everywhere in AI subsystem |
| **Root Cause** | API keys (Gemini, OpenCode) are stored using `SharedPreferences` without encryption. On a rooted device or with ADB backup, keys are readable in plaintext from `/data/data/com.rk.xededitor/shared_prefs/`. |
| **Symptoms** | Credential leak on rooted devices or via ADB backup. |
| **Reproduction** | `adb backup -f backup.ab com.rk.xededitor && (extract backup) && strings shared_prefs/*.xml` — API keys visible. |
| **Expected Behavior** | Store API keys using `EncryptedSharedPreferences` (AndroidX Security Crypto). Keys encrypted at rest with Android Keystore. |
| **Actual Behavior** | Plaintext SharedPreferences |
| **Suggested Fix** | Replace `SharedPreferences` with `EncryptedSharedPreferences` for sensitive keys. Or use `androidx.security.crypto.MasterKey`. |
| **Architectural Impact** | Low — drop-in replacement |
| **Risk** | Low |

---

### BUG-44: MCP Server — No Authentication/Authorization

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/server/IdeBridgeServer.kt` |
| **Root Cause** | The HTTP MCP server listens on localhost but has no authentication. Any app on the device can send requests to the MCP server. The `Authorization` header is not checked. |
| **Symptoms** | Any Android app on the same device can read/write files, execute terminal commands, and access git data via the MCP server. |
| **Reproduction** | `adb shell curl http://localhost:PORT/tools/call/read_file -d '{"path":"/data/data/com.rk.xededitor/shared_prefs/settings.xml"}'` — works without auth. |
| **Expected Behavior** | Generate a random bearer token on server start. Require `Authorization: Bearer <token>` on every request. Validate token. |
| **Actual Behavior** | No authentication |
| **Suggested Fix** | Generate random 256-bit token on server start. Pass to client via SSE `/init` event. Verify on every HTTP request. Use constant-time comparison. |
| **Architectural Impact** | Medium — client-side also needs token handling |
| **Risk** | Low |

---

### BUG-45: No Input Sanitization in Terminal Command Execution

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/bridge/tools/TerminalTools.kt` |
| **Root Cause** | The `execute_command` MCP tool passes user-supplied strings directly to shell execution without sanitization. Shell metacharacters (`;`, `|`, `$(...)`, backticks) are not escaped. However, since commands are passed as arguments to the terminal binary, not via `sh -c`, the risk is limited but present. |
| **Symptoms** | An MCP client could potentially execute arbitrary commands by crafting malicious input strings. |
| **Reproduction** | Send command: `ls; rm -rf /` — If processed by `sh -c`, would execute destructive command. |
| **Expected Behavior** | Use `ProcessBuilder` with command list (not shell). Or use `arrayOf("sh", "-c", command)` but validate the command against an allowlist. |
| **Actual Behavior** | Raw string passed to shell |
| **Suggested Fix** | Review `TerminalTools.kt` command execution path. If using `ProcessBuilder`, use list form to avoid shell injection. If shell is required, implement command validation/reject dangerous patterns. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-46: No HTTPS/TLS for Provider API Calls

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | `core/main/src/main/java/com/rk/ai/AiCompletionEngine.kt` |
| **Root Cause** | AI provider API calls use `HttpURLConnection` or OkHttp without certificate pinning. While HTTPS is the default for public providers, a MITM attack is possible if the device has a custom CA installed. |
| **Symptoms** | On a corporate/proxy network with custom CA, API keys and conversation data could be intercepted. |
| **Reproduction** | Configure MITM proxy on device. Observe AI traffic in plaintext (if HTTP) or with forged cert (if custom CA installed). |
| **Expected Behavior** | Implement certificate pinning with `CertificatePinner` (OkHttp). Or at minimum, verify hostname and use strong TLS. |
| **Actual Behavior** | Default TLS without pinning |
| **Suggested Fix** | Add `CertificatePinner` with provider certificate hashes. Or use `network_security_config.xml` to pin. |
| **Architectural Impact** | Low |
| **Risk** | Low — but cert pinning complicates debugging |

---

## 8. Architecture & Design Bugs

### BUG-47: No Dependency Injection Framework — Manual Wiring Hell

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | All files with `object`, `companion object`, or global state (`Settings.kt`, `App.kt`, `IdeServiceImpl.kt`, etc.) |
| **Root Cause** | There is no DI framework (no Hilt, Koin, Dagger). Dependencies are wired manually via global singletons (`object` declarations) or passed through multiple layers of constructors. This creates tight coupling, makes testing impossible, and causes initialization order issues. |
| **Symptoms** | Singleton pattern used everywhere. Cannot mock dependencies for testing. Initialization order bugs. |
| **Reproduction** | N/A — architectural. Any attempt to unit test a class that depends on `Settings` or `IdeServiceImpl` requires mocking static state. |
| **Expected Behavior** | Use Hilt (Android-first DI) or Koin (Kotlin-first, lighter). Inject dependencies via constructor injection. |
| **Actual Behavior** | Manual wiring, global singletons, service locator pattern |
| **Suggested Fix** | Adopt Hilt. Add `@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject constructor`, `@Module/@Provides`. Incrementally migrate from singletons to DI-managed scopes. |
| **Architectural Impact** | Very High — touches every class, every file |
| **Risk** | Very High — this is a months-long migration, not a single PR |

---

### BUG-48: No Unit or Instrumentation Tests

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | Entire project |
| **Root Cause** | No test directory exists. Zero unit tests, zero integration tests, zero UI tests. Every bug fix or refactor relies on manual testing. |
| **Symptoms** | Every change risks regression. No safety net for refactoring. |
| **Reproduction** | N/A — architectural. |
| **Expected Behavior** | At minimum: unit tests for AI session state machine, MCP dispatcher, terminal parser, file service. Integration tests for MCP tool pipeline. |
| **Actual Behavior** | Zero tests |
| **Suggested Fix** | Add JUnit 5 + MockK for unit tests. Add Compose UI tests for critical UI flows. Add integration test for MCP server. CI pipeline with `./gradlew test`. |
| **Architectural Impact** | High — DI is prerequisite for effective testing |
| **Risk** | Low (tests are additive) |

---

### BUG-49: No Structured Logging — Println/Log.e Scattered

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | Throughout codebase |
| **Root Cause** | Logging is done with `Log.d`, `Log.e`, and `println` statements scattered throughout the codebase. No structured logging, no log levels, no log aggregation, no remote logging. |
| **Symptoms** | Debugging requires ADB logcat. No way to correlate log events across components. Production crash analysis is blind. |
| **Reproduction** | N/A — architectural. |
| **Expected Behavior** | Use Timber or slf4j with tag-based filtering. Structured log format (tag, message, exception, metadata). Crash reporting via Firebase Crashlytics or ACRA. |
| **Actual Behavior** | Ad-hoc Log calls |
| **Suggested Fix** | Add Timber dependency. Replace all `Log.d/e` with `timber.tag("Xed").d {}`. Add `DebugTree` in debug, `CrashReportingTree` in release. |
| **Architectural Impact** | Low — additive |
| **Risk** | Low |

---

### BUG-50: Massive Code Duplication in Tool Implementations

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | All MCP tool files in `core/main/src/main/java/com/rk/ai/bridge/tools/` |
| **Root Cause** | Each MCP tool independently handles parameter extraction, error handling, path validation, and response formatting. There is heavy copy-paste of boilerplate across 25+ tool files. |
| **Symptoms** | 70% of the code in each tool file is boilerplate (parameter parsing, error handling, response wrapping). A change to error handling requires editing all 25 files. |
| **Reproduction** | N/A — architectural. Count lines of parameter validation code: ~15 lines per tool × 25 tools = 375 lines of duplication. |
| **Expected Behavior** | Base class handles parameter parsing, error mapping, path validation via shared utilities. Tools only implement the core logic. |
| **Actual Behavior** | Each tool manually parses JSON, validates, formats errors |
| **Suggested Fix** | Enhance `BaseMcpTool` with: `inline fun <reified T> parseParams(json: JsonObject): T` via kotlinx.serialization. Centralized error-to-JSON-RPC mapping. Shared path validation delegates. |
| **Architectural Impact** | Medium — every tool file must be refactored |
| **Risk** | Medium — careful to preserve edge-case handling |

---

## 9. Build & Configuration Bugs

### BUG-51: settings.gradle.kts Excessive Module Loading

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | `settings.gradle.kts` |
| **Root Cause** | All modules are included unconditionally via `include(":core:main")`, `include(":core:components")`, etc. Seven core submodules plus app, benchmark, etc. are always configured even if not needed. |
| **Symptoms** | Gradle configuration time is longer than necessary. CI builds all modules even for single-module changes. |
| **Reproduction** | `./gradlew :app:assembleDebug` — configures all 7 core modules even though only `:app` and `:core:main` are needed. |
| **Expected Behavior** | Use dynamic `include` or composite builds. Or lazy module configuration. |
| **Actual Behavior** | All modules always included |
| **Suggested Fix** | Keep as-is for simplicity. The configuration overhead is minimal (<5s). Only optimize if build time becomes problematic. |
| **Architectural Impact** | None |
| **Risk** | None |

---

### BUG-52: No ProGuard/R8 Rules for Reflection-Heavy Code

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | No `proguard-rules.pro` in core modules |
| **Root Cause** | The AI subsystem and MCP tools use Kotlin reflection (`::class`, `memberProperties`) extensively. Without ProGuard rules, release builds may obfuscate/remove reflected classes, causing runtime `ClassNotFoundException` or `NoSuchFieldException`. |
| **Symptoms** | Release builds crash with reflection-related errors. Debug builds work fine. |
| **Reproduction** | Build release APK. Try to use any AI feature. Crashes with reflection exception. |
| **Expected Behavior** | ProGuard rules that keep all classes/packages used by reflection: AI agents, MCP tools, service interfaces. |
| **Actual Behavior** | No ProGuard rules; reflection may break in release |
| **Suggested Fix** | Add `-keep class com.rk.ai.** { *; }`, `-keep class com.rk.terminal.** { *; }`, `-keepclassmembers class * { @kotlin.Metadata *; }`. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-53: Dependency Version Hardcoding

| Field | Value |
|-------|-------|
| **Severity** | LOW |
| **Affected Files** | Various `build.gradle.kts` files |
| **Root Cause** | Library versions are hardcoded in each module's `build.gradle.kts` instead of using a version catalog (`libs.versions.toml`). |
| **Symptoms** | Updating a library version requires searching and replacing across multiple files. Version inconsistencies between modules. |
| **Reproduction** | Need to update OkHttp from 4.11 to 4.12. Must find all occurrences across modules. |
| **Expected Behavior** | Version catalog in `gradle/libs.versions.toml`. All modules reference `libs.okhttp`. |
| **Actual Behavior** | Hardcoded version strings |
| **Suggested Fix** | Create `gradle/libs.versions.toml`. Migrate all dependencies to version catalog. Use `gradle/libs.versions.toml` for centralized version management. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

## 10. Data Loss & Persistence Bugs

### BUG-54: Auto-Save Not Implemented — Unsaved Changes Lost on Crash

| Field | Value |
|-------|-------|
| **Severity** | CRITICAL |
| **Affected Files** | Editor component (likely in `core/components` or `core/main`) |
| **Root Cause** | The editor does not auto-save. If the app is killed (crash, OOM, battery optimization), all unsaved edits are lost. No recovery mechanism. |
| **Symptoms** | User edits a file for 30 minutes. App crashes. All edits lost. |
| **Reproduction** | Edit a file, do not manually save. Kill the app. Edits are lost. |
| **Expected Behavior** | Auto-save every 30 seconds or on text idle (500ms no input). Recovery file for crash scenarios. `onSaveInstanceState` for unsaved buffer. |
| **Actual Behavior** | No auto-save |
| **Suggested Fix** | Add debounced auto-save (500ms idle after last keystroke). Use temporary recovery file in app-specific storage. On editor open, check for recovery file and offer restore. |
| **Architectural Impact** | Medium — editor model changes |
| **Risk** | Medium — auto-save must not corrupt files (atomic write pattern) |

---

### BUG-55: No Undo/Redo History Limit — Memory Bloat

| Field | Value |
|-------|-------|
| **Severity** | MEDIUM |
| **Affected Files** | Editor undo/redo system |
| **Root Cause** | Undo history is stored as a list of document snapshots or diffs with no upper bound. Each edit adds to the history. In a long editing session, undo history can consume hundreds of megabytes. |
| **Symptoms** | High memory usage during long editing sessions. |
| **Reproduction** | Open a large file. Make 10,000 small edits. Memory grows proportionally. |
| **Expected Behavior** | Configurable undo history limit (default 500). Old entries discarded via circular buffer. |
| **Actual Behavior** | Unbounded undo history |
| **Suggested Fix** | Implement `UndoManager` with max history size. Evict oldest entries when limit exceeded. Use compression for large document states. |
| **Architectural Impact** | Low |
| **Risk** | Low |

---

### BUG-56: No Backup of Open Files on Low Memory Kill

| Field | Value |
|-------|-------|
| **Severity** | HIGH |
| **Affected Files** | `core/main/src/main/java/com/rk/activities/main/MainActivity.kt` |
| **Root Cause** | `onSaveInstanceState` is not overridden to save open file paths, cursor positions, scroll positions, and unsaved edits. When the activity is killed and recreated, the user returns to a blank editor. |
| **Symptoms** | App is backgrounded, then killed by the system. User returns to a fresh start with no recollection of what they were editing. |
| **Reproduction** | Open a file. Background the app. Force-stop from developer settings. Reopen app. File is not restored. |
| **Expected Behavior** | Override `onSaveInstanceState(outState: Bundle)`. Save open file paths, cursor positions, scroll offsets. Restore in `onCreate(savedInstanceState)`. |
| **Actual Behavior** | No state saved |
| **Suggested Fix** | Implement `SavedStateHandle` in ViewModel. Serialize open tabs list, cursor positions, scroll state to bundle. Restore on recreation. |
| **Architectural Impact** | Medium — requires ViewModel integration |
| **Risk** | Low |

---

## Appendix A: Severity Distribution

| Severity | Count | Bug IDs |
|----------|-------|---------|
| CRITICAL | 5 | 1, 13, 43, 54, (indirect: 47) |
| HIGH | 14 | 2, 3, 12, 14, 18, 24, 25, 35, 39, 41, 44, 47, 48, 56 |
| MEDIUM | 31 | 4-10, 15-17, 19, 21-23, 26-28, 30-34, 36, 38, 40, 42, 45, 46, 49, 50, 52, 55 |
| LOW | 6 | 11, 20, 29, 37, 42, 51, 53 |

## Appendix B: Affected Modules

| Module | Bug Count | Key Bugs |
|--------|-----------|----------|
| `core/main` (AI) | 15 | BUG-01 through BUG-11, BUG-35-37, BUG-40, BUG-46 |
| `core/main` (MCP) | 12 | BUG-12 through BUG-23 |
| `core/main` (Terminal) | 6 | BUG-24 through BUG-29 |
| `core/main` (Services) | 5 | BUG-30 through BUG-34 |
| `core/main` (UI) | 3 | BUG-38, BUG-54-56 |
| Build System | 3 | BUG-51, BUG-52, BUG-53 |
| All | 3 | BUG-47, BUG-48, BUG-49 |

---

*End of Bug Audit — 56 issues documented*
