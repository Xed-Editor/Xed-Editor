# Xed-Editor Architecture & Systems Documentation (pro.md)

> **Version:** Current (pre-refactor snapshot)  
> **Date:** 2026-05-23  
> **Scope:** Complete architecture analysis of all existing subsystems — AI, MCP Bridge, Terminal, Services, UI, Settings, Build

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Module Architecture](#2-module-architecture)
3. [AI Subsystem](#3-ai-subsystem)
4. [MCP Bridge Subsystem](#4-mcp-bridge-subsystem)
5. [Terminal Engine](#5-terminal-engine)
6. [Service Layer](#6-service-layer)
7. [UI Layer](#7-ui-layer)
8. [Settings & Configuration](#8-settings--configuration)
9. [Build & Dependency System](#9-build--dependency-system)
10. [Data Flow Diagrams](#10-data-flow-diagrams)
11. [Cross-Cutting Concerns](#11-cross-cutting-concerns)
12. [Architecture Decisions & Trade-offs](#12-architecture-decisions--trade-offs)
13. [Refactoring Target Architecture](#13-refactoring-target-architecture)

---

## 1. Project Overview

### 1.1 Purpose
Xed-Editor is an Android-native code editor with integrated AI agent capabilities, an MCP (Model Context Protocol) bridge server, and a full terminal emulator. It targets developers who need an on-device editing experience with AI assistance.

### 1.2 Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin (≥1.9) |
| UI Framework | Jetpack Compose (AndroidView interop for terminal) |
| Async Framework | Kotlin Coroutines + Flow |
| DI | None (manual wiring / global singletons) |
| HTTP Client | OkHttp / HttpURLConnection |
| JSON | kotlinx.serialization / Gson |
| AI Providers | Gemini API, OpenCode API |
| MCP Protocol | JSON-RPC 2.0 over HTTP SSE |
| Terminal | Termux JNI-based terminal emulation |
| LSP Client | Custom LSP implementation |
| Git | JGit (pure Java Git implementation) |
| Build System | Gradle (KTS) with multi-module |
| Min SDK | API 26 (Android 8.0) |

### 1.3 Project Structure (Top-Level)

```
Xed-Editor/
├── app/                          # Main application module
│   ├── src/main/java/com/rk/xed/ # App entry, theming, activities
│   └── build.gradle.kts
├── core/
│   ├── main/                     # Core logic (~90% of all code)
│   │   ├── src/main/java/com/rk/
│   │   │   ├── ai/               # AI subsystem (agents, sessions, MCP)
│   │   │   ├── terminal/         # Terminal emulator
│   │   │   ├── activities/       # UI (main activity, tabs, navigation)
│   │   │   ├── settings/         # Settings system
│   │   │   └── [other packages]  # Editors, file management, etc.
│   │   └── build.gradle.kts
│   ├── components/               # Reusable UI components
│   ├── extension/                # Extension API
│   ├── resources/                # Shared resources
│   ├── terminal-emulator/        # Low-level terminal JNI
│   ├── terminal-view/            # Terminal Compose view
│   └── termux-shared/            # Termux shared utilities
├── plugin-sdk/                   # Plugin development SDK
├── benchmark/                    # Performance benchmarks
├── baselineprofile/              # Baseline profiles
├── fastlane/                     # CI/CD metadata
├── docs/                         # Documentation
├── gradle/                       # Gradle wrapper, config
├── .github/                      # GitHub Actions CI
└── settings.gradle.kts
```

---

## 2. Module Architecture

### 2.1 Module Dependency Graph

```
app ──────────────► core:main ──► core:components
                      │              │
                      │              ▼
                      │         core:resources
                      │              │
                      │              ▼
                      │         core:extension
                      │
                      ├─────► core:terminal-emulator (JNI)
                      │              │
                      │              ▼
                      │         core:terminal-view (Compose)
                      │
                      └─────► core:termux-shared
```

### 2.2 Module Responsibilities

| Module | Responsibility | Key Packages |
|--------|---------------|--------------|
| `app` | Application entry, theme, navigation graph | `com.rk.xed` |
| `core:main` | Everything — AI, MCP, terminal, UI, services | `com.rk.ai`, `com.rk.terminal`, `com.rk.activities` |
| `core:components` | Reusable editor components | Editor views, syntax highlighting |
| `core:extension` | Plugin/extension system | Extension loading, API |
| `core:resources` | Strings, drawables, layouts | Shared resources |
| `core:terminal-emulator` | Termux JNI terminal backend | Terminal emulation, VT100/xterm |
| `core:terminal-view` | Compose terminal widget | TerminalScreen composable |
| `core:termux-shared` | Termux shared preferences/utilities | Termux settings |

### 2.3 Key Architectural Observations

- **Massive `core:main` module**: This single module contains AI, MCP, terminal logic, service layer, all UI screens, settings, and file management. It is a "megamodule" with high coupling between seemingly unrelated subsystems.
- **No module boundaries for AI**: AI agents, sessions, MCP bridge, and services all live in `com.rk.ai.*` within `core:main`. There is no separate `ai-core` or `ai-api` module.
- **JNI terminal bridge**: Terminal functionality depends on native `.so` libraries via Termux. The `terminal-emulator` and `terminal-view` modules wrap these JNI calls.

---

## 3. AI Subsystem

### 3.1 Package Map

```
com.rk.ai/
├── AiCompletionEngine.kt     # Core engine: orchestrates AI completions
├── AiConfig.kt               # Configuration (endpoints, models, keys)
├── AiSessionManager.kt       # Session state management
├── AgentModelResolver.kt     # Model name resolution via API
├── AgentCli.kt               # CLI interface for terminal-based AI
├── InlineAgentBar.kt         # Inline AI completion bar (editor overlay)
├── AiAgentSheet.kt           # Full AI chat bottom sheet
├── IdeBridge.kt              # Bridge interface to IDE features
├── IdeWorkspace.kt           # Workspace/project abstraction
├── ProjectConfig.kt          # Per-project AI configuration
│
├── agents/                   # Agent implementations
│   ├── AiAgent.kt            # Agent interface
│   ├── GeminiAgent.kt        # Gemini API implementation
│   ├── OpenCodeAgent.kt      # OpenCode API implementation
│   ├── AgentProfile.kt       # Agent metadata/profile
│   └── AgentTypeRegistry.kt  # Agent type registry
│
├── session/                  # Session management
│   ├── AiSessionManager.kt   # (referenced above — actual location: ai/)
│   └── AgentEnvironmentBuilder.kt  # Builds agent execution environment
│
├── service/                  # IDE service interfaces & implementations
│   ├── IdeService.kt         # Top-level IDE service interface
│   ├── IdeServiceImpl.kt     # Implementation aggregating sub-services
│   ├── FileService.kt        # File operations
│   ├── EditorService.kt      # Editor operations
│   ├── GitService.kt         # Git operations
│   ├── TerminalService.kt    # Terminal operations
│   ├── ProjectService.kt     # Project management
│   ├── LspService.kt         # LSP operations
│   └── *Ops.kt               # Individual operation interfaces
│
├── bridge/                   # MCP bridge core
│   ├── McpTool.kt            # MCP tool interface
│   ├── McpToolRegistry.kt    # Tool registration
│   ├── DiscoveryFileWriter.kt # MCP discovery file writer
│   ├── IdeNotificationSender.kt  # IDE notification via MCP
│   └── NotificationHelper.kt # Notification utilities
│
├── bridge/server/            # MCP HTTP server
│   ├── IdeBridgeServer.kt    # NanoHTTPD-based HTTP server
│   ├── McpDispatcher.kt     # JSON-RPC method dispatcher
│   ├── SseManager.kt        # SSE connection manager
│   └── HttpSessionTracker.kt # HTTP session tracking
│
└── bridge/tools/             # MCP tool implementations (25+ files)
    ├── BaseMcpTool.kt        # Abstract base class
    ├── McpToolHelpers.kt     # Shared utilities
    ├── FileTools.kt          # File read/write/search
    ├── EditorTools.kt        # Editor manipulation
    ├── SystemTools.kt        # System info
    ├── SearchTools.kt        # Code search
    ├── GitTools.kt           # Git operations
    ├── TerminalTools.kt      # Terminal command execution
    ├── WebTools.kt           # Web fetch
    ├── AdvancedTools.kt      # Complex operations
    ├── BatchTools.kt         # Batch operations
    ├── EditFileTool.kt       # File editing
    ├── CodeFrameTool.kt      # Code context frame
    ├── DiffTools.kt          # Diff operations
    ├── LspTools.kt           # LSP integration
    ├── RenameFormatTools.kt  # Rename/format
    ├── StructureTools.kt     # Code structure
    ├── CursorTools.kt        # Cursor operations
    ├── GuidelineTools.kt     # Coding guidelines
    ├── NativeTerminalTools.kt # Native terminal operations
    ├── FileInfoTool.kt       # File metadata
    ├── SearchReplaceTool.kt  # Search and replace
    ├── ReadProjectFilesTool.kt # Project file reading
    └── ToolError.kt          # Error type
```

### 3.2 AI Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│                   AI COMPLETION FLOW                 │
│                                                      │
│  User Input                                          │
│    │                                                  │
│    ├─► InlineAgentBar (editor overlay)               │
│    │     │                                            │
│    │     └─► AiCompletionEngine                       │
│    │           │                                      │
│    ├─► AiAgentSheet (chat sheet)                     │
│    │     │                                            │
│    │     └─► AiSessionManager (state machine)         │
│    │           │  ┌─────────────────┐                 │
│    │           ├──│  AgentTypeRegistry │              │
│    │           │  └────────┬────────┘                 │
│    │           │     resolves to                      │
│    │           │           │                          │
│    │           │  ┌───────▼────────┐                  │
│    │           ├──│ GeminiAgent    │                  │
│    │           │  │ OpenCodeAgent  │                  │
│    │           │  └───────┬────────┘                  │
│    │           │           │                          │
│    │           │  ┌───────▼────────┐                  │
│    │           └──│ AgentEnvironment│                 │
│    │              │ Builder        │                  │
│    │              └────────────────┘                  │
│    │                                                  │
│    └────────────────────────────────────► HTTP API    │
│                                          (Gemini/OpenCode)│
└─────────────────────────────────────────────────────┘
```

### 3.3 Key Components

#### 3.3.1 AiCompletionEngine
- **File**: `core/main/src/main/java/com/rk/ai/AiCompletionEngine.kt`
- **Role**: Central orchestrator for all AI completions
- **Consumers**: InlineAgentBar, AiAgentSheet, AgentCli
- **Dependencies**: AiConfig, AiSessionManager, AgentModelResolver
- **State**: Stateless (delegates state to AiSessionManager)
- **Threading**: Uses `CoroutineScope(Dispatchers.IO)` for network calls
- **Key functions**:
  - `suspend fun complete(prompt, agentType, params): Result<AiResponse>`
  - `suspend fun streamComplete(prompt, agentType, onChunk): Flow<AiChunk>`

#### 3.3.2 AiSessionManager
- **File**: `core/main/src/main/java/com/rk/ai/session/AiSessionManager.kt`
- **Role**: Manages AI session lifecycle and state transitions
- **State Machine**: `IDLE → THINKING → RESPONDING → IDLE`
- **State**: `MutableStateFlow<AiSessionState>` (singleton via `object` declaration)
- **Threading**: `synchronized` blocks with `runBlocking` for state transitions
- **Issues**: See BUG-01, BUG-05 in bug.md

#### 3.3.3 GeminiAgent / OpenCodeAgent
- **Files**: `core/main/src/main/java/com/rk/ai/agents/{GeminiAgent,OpenCodeAgent}.kt`
- **Role**: AI provider-specific HTTP client implementations
- **Interface**: `AiAgent` (in `AiAgent.kt`)
  ```kotlin
  interface AiAgent {
      suspend fun complete(request: AiRequest): AiResponse
      suspend fun stream(request: AiRequest): Flow<AiChunk>
      val profile: AgentProfile
  }
  ```
- **Key differences**:
  - GeminiAgent: Vertex AI / Google AI Gemini API, uses API key auth
  - OpenCodeAgent: Self-hosted OpenCode server, uses bearer token auth
- **Both**: Have no retry logic, no rate limiting, no circuit breaker

#### 3.3.4 AgentTypeRegistry
- **File**: `core/main/src/main/java/com/rk/ai/agents/AgentTypeRegistry.kt`
- **Role**: Maps string agent type identifiers to `AiAgent` implementations
- **Registration**: Manual via `register("gemini", GeminiAgent())`, `register("opencode", OpenCodeAgent())`
- **Pattern**: Service locator (anti-pattern — global mutable registry)

#### 3.3.5 AgentEnvironmentBuilder
- **File**: `core/main/src/main/java/com/rk/ai/session/AgentEnvironmentBuilder.kt`
- **Role**: Builds execution environment context for AI agents
- **Output**: `AgentEnvironment` data class with workspace path, session ID, IDE state snapshot
- **Used by**: AiSessionManager before delegating to AiAgent

#### 3.3.6 AgentModelResolver
- **File**: `core/main/src/main/java/com/rk/ai/AgentModelResolver.kt`
- **Role**: Resolves model name to available model endpoint
- **Mechanism**: HTTP GET to provider's model list API, finds matching model
- **Caching**: None (see BUG-04)

#### 3.3.7 AgentCli
- **File**: `core/main/src/main/java/com/rk/ai/AgentCli.kt`
- **Role**: Allows AI interaction from the terminal
- **Commands**: `/ai`, `/ai:clear`, `/ai:config`
- **Implementation**: Reads from terminal input stream, writes AI responses to terminal output

#### 3.3.8 InlineAgentBar
- **File**: `core/main/src/main/java/com/rk/ai/InlineAgentBar.kt`
- **Role**: Overlay bar in the editor showing AI inline completions
- **Implementation**: Compose `Popup` or overlay positioned near cursor
- **Interaction**: Accept/reject suggestion, shows model loading state

#### 3.3.9 AiAgentSheet
- **File**: `core/main/src/main/java/com/rk/ai/AiAgentSheet.kt`
- **Role**: Full-screen bottom sheet for AI chat conversations
- **Implementation**: Modal bottom sheet with message list, input field, send button
- **State**: Composable-local `remember` (lost on config change — see BUG-35)

### 3.4 AI Data Flow (Complete Chat Request)

```
1. User types message in AiAgentSheet
2. Sheet calls AiSessionManager.sendMessage(text)
3. SessionManager transitions to THINKING state
4. SessionManager calls AgentEnvironmentBuilder.build() → AgentEnvironment
5. SessionManager resolves agent type via AgentTypeRegistry
6. SessionManager calls agent.complete(request) with environment context
7. Agent makes HTTP POST to AI provider API
8. Agent returns AiResponse (or streams AiChunk via Flow)
9. SessionManager transitions to RESPONDING state
10. SessionManager emits response chunks via StateFlow
11. AiAgentSheet collects Flow, renders messages in chat UI
12. SessionManager transitions back to IDLE
```

### 3.5 AI Configuration (AiConfig)

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `provider` | String | `"gemini"` | AI provider type |
| `api_key` | String | `""` | API key (stored in plaintext) |
| `api_endpoint` | String | `""` | Custom API endpoint URL |
| `model` | String | `"gemini-2.0-flash"` | Model name |
| `temperature` | Float | `0.7f` | Temperature parameter |
| `max_tokens` | Int | `4096` | Max response tokens |
| `system_prompt` | String | `""` | Custom system prompt |

---

## 4. MCP Bridge Subsystem

### 4.1 Architecture Overview

```
┌────────────────────────────────────────────────────────────┐
│                   MCP BRIDGE ARCHITECTURE                    │
│                                                              │
│  External MCP Client (IDE, CLI, Web)                         │
│         │                                                    │
│         ├── HTTP POST /tools/call → JSON-RPC request         │
│         ├── HTTP GET  /sse      → SSE event stream           │
│         ├── HTTP POST /sse      → Send client messages       │
│         │                                                    │
│         ▼                                                    │
│  ┌──────────────────┐                                        │
│  │  IdeBridgeServer  │  NanoHTTPD (port 8080 by default)     │
│  │  (HTTP Server)    │                                        │
│  └────────┬─────────┘                                        │
│           │                                                  │
│           ├────────────────────────────────────┐              │
│           │  POST /tools/call                  │  GET /sse   │
│           ▼                                    ▼              │
│  ┌────────────────────┐             ┌──────────────┐         │
│  │   McpDispatcher    │             │  SseManager   │         │
│  │  (JSON-RPC router) │             │ (SSE conns)   │         │
│  └────────┬───────────┘             └──────────────┘         │
│           │                                                  │
│           ▼                                                  │
│  ┌────────────────────┐                                      │
│  │  McpToolRegistry   │  Maps method → McpTool instance      │
│  └────────┬───────────┘                                      │
│           │                                                  │
│           ▼                                                  │
│  ┌──────────────────────────────────────────┐                │
│  │       25+ MCP Tool Implementations       │                │
│  │  (FileTools, GitTools, TerminalTools...)  │                │
│  └──────────────────────────────────────────┘                │
│                                                              │
│  ┌──────────────────────────────────────────┐                │
│  │          Service Layer (IdeServiceImpl)   │                │
│  │  FileService │ GitService │ LspService... │                │
│  └──────────────────────────────────────────┘                │
└────────────────────────────────────────────────────────────┘
```

### 4.2 Server Component Details

#### 4.2.1 IdeBridgeServer
- **File**: `core/main/src/main/java/com/rk/ai/bridge/server/IdeBridgeServer.kt`
- **Framework**: NanoHTTPD (single-threaded selector loop + worker thread pool)
- **Port**: Configurable via Settings (default: 8080)
- **Routes**:
  - `POST /tools/call` — JSON-RPC tool invocation
  - `GET /sse` — SSE event stream (text/event-stream)
  - `POST /sse` — Client-to-server messages over SSE
  - `GET /health` — Health check endpoint
- **Lifecycle**: Start on app launch (or on first MCP access), stop on app destroy
- **Threading**: NanoHTTPD uses `ServerRunner` with fixed thread pool

#### 4.2.2 McpDispatcher
- **File**: `core/main/src/main/java/com/rk/ai/bridge/server/McpDispatcher.kt`
- **Role**: Parse JSON-RPC request, route to tool, format response
- **JSON-RPC Support**: 
  - Single requests (method, params, id)
  - Notifications (no id, no response)
  - Error responses (code, message, data)
- **Issues**: Uses `runBlocking` to call suspend tool functions (see BUG-13)

#### 4.2.3 SseManager
- **File**: `core/main/src/main/java/com/rk/ai/bridge/server/SseManager.kt`
- **Role**: Manages Server-Sent Events connections for streaming responses
- **State**: ConcurrentHashMap of active SSE connections (keyed by session ID)
- **Events**:
  - `message` — Tool output, streaming chunks
  - `error` — Error events
  - `ready` — Connection established
  - `heartbeat` — Keep-alive (not implemented — see BUG-14)
- **Format**: Standard SSE (`data: {...}\n\n`)

#### 4.2.4 HttpSessionTracker
- **File**: `core/main/src/main/java/com/rk/ai/bridge/server/HttpSessionTracker.kt`
- **Role**: Tracks HTTP sessions for stateful interactions
- **Session Data**: Creation time, last access time, associated SSE connection
- **Issues**: No session expiry (see BUG-15)

#### 4.2.5 McpToolRegistry
- **File**: `core/main/src/main/java/com/rk/ai/bridge/McpToolRegistry.kt`
- **Role**: Central registry of all MCP tools
- **Registration**: `registerAll()` method that hardcodes every tool constructor
- **Lookup**: `getTool(methodName: String): McpTool?`
- **Listing**: `listTools(): List<ToolDescription>` for capability discovery

#### 4.2.6 DiscoveryFileWriter
- **File**: `core/main/src/main/java/com/rk/ai/bridge/DiscoveryFileWriter.kt`
- **Role**: Writes MCP discovery JSON to a well-known path so MCP clients can auto-discover the server
- **Output**: `{ "name": "xed-mcp", "version": "1.0", "tools": [...], "protocol": "sse" }`
- **Trigger**: Written on server start

### 4.3 MCP Tool API

All tools extend `BaseMcpTool` and implement:

```kotlin
interface McpTool {
    val name: String           // JSON-RPC method name (e.g., "read_file")
    val description: String    // Human-readable description
    val parameters: List<ToolParameter>  // JSON Schema for params
    
    suspend fun execute(params: JsonObject, context: ToolContext): ToolResult
}
```

**Standard response format:**
```json
{
    "jsonrpc": "2.0",
    "id": 1,
    "result": {
        "content": [{
            "type": "text",
            "text": "..."
        }],
        "isError": false
    }
}
```

### 4.4 Complete Tool Inventory

| Tool Name | File | Category | Description | Status |
|-----------|------|----------|-------------|--------|
| `read_file` | FileTools.kt | File | Read file contents | Stable |
| `write_file` | FileTools.kt | File | Write file contents | Stable |
| `read_files` | ReadProjectFilesTool.kt | File | Batch read files | Beta |
| `edit_file` | EditFileTool.kt | File | Edit file with diff | Stable |
| `file_info` | FileInfoTool.kt | File | Get file metadata | Stable |
| `search_code` | SearchTools.kt | Search | Text search in project | Stable |
| `search_replace` | SearchReplaceTool.kt | Search | Search and replace | Beta |
| `editor_open` | EditorTools.kt | Editor | Open file in editor | Stable |
| `editor_close` | EditorTools.kt | Editor | Close editor tab | Stable |
| `editor_insert` | EditorTools.kt | Editor | Insert text at cursor | Stable |
| `list_files` | StructureTools.kt | Structure | List directory contents | Stable |
| `get_project_structure` | StructureTools.kt | Structure | Get project tree | Stable |
| `cursor_get` | CursorTools.kt | Cursor | Get cursor position | Stable |
| `cursor_set` | CursorTools.kt | Cursor | Set cursor position | Stable |
| `git_status` | GitTools.kt | Git | Git status | Stable |
| `git_diff` | GitTools.kt | Git | Git diff | Stable |
| `git_log` | GitTools.kt | Git | Git commit log | Stable |
| `git_commit` | GitTools.kt | Git | Create commit | Beta |
| `execute_command` | TerminalTools.kt | Terminal | Execute terminal command | Beta |
| `native_terminal` | NativeTerminalTools.kt | Terminal | Native terminal access | Alpha |
| `web_fetch` | WebTools.kt | Web | Fetch web content | Stable |
| `lsp_completion` | LspTools.kt | LSP | Code completion | Alpha |
| `lsp_hover` | LspTools.kt | LSP | Hover info | Alpha |
| `lsp_definition` | LspTools.kt | LSP | Go to definition | Alpha |
| `lsp_references` | LspTools.kt | LSP | Find references | Alpha |
| `rename_symbol` | RenameFormatTools.kt | LSP | Rename symbol | Alpha |
| `format_document` | RenameFormatTools.kt | LSP | Format document | Alpha |
| `get_code_frame` | CodeFrameTool.kt | Structure | Get code context | Beta |
| `diff` | DiffTools.kt | Utility | Compute diff | Stable |
| `apply_diff` | DiffTools.kt | Utility | Apply diff | Beta |
| `get_guidelines` | GuidelineTools.kt | Utility | Get coding guidelines | Stable |
| `batch_execute` | BatchTools.kt | Batch | Execute multiple tools | Alpha |
| `get_system_info` | SystemTools.kt | System | System information | Stable |
| `get_diagnostics` | AdvancedTools.kt | System | IDE diagnostics | Alpha |
| `notify` | IdeNotificationSender.kt | Notification | Send notification | Stable |

**Stability Legend:**
- **Stable**: No known issues, used in production
- **Beta**: Works but known edge cases or incomplete error handling
- **Alpha**: Recently added, may have untested edge cases

### 4.5 Tool Execution Pipeline

```
1. HTTP Request arrives at IdeBridgeServer.serve()
2. Server reads body, parses as JSON-RPC object
3. Server calls McpDispatcher.dispatch(method, params, id)
4. Dispatcher calls McpToolRegistry.getTool(methodName)
5. Dispatcher constructs ToolContext (session, auth, request info)
6. Dispatcher calls tool.execute(params, context)  [via runBlocking]
7. Tool executes operation (file I/O, git, HTTP, terminal, LSP)
8. Tool returns ToolResult (success with content, or error)
9. Dispatcher formats JSON-RPC response
10. Server writes HTTP response
```

### 4.6 Streaming (SSE) Pipeline

```
1. Client opens GET /sse → establishes SSE connection
2. SseManager registers connection (keyed by session ID)
3. Tool with streaming calls sseManager.send(sessionId, event)
   - Terminal command output chunks
   - AI streaming response chunks
4. Client receives events in real-time
5. Client can send messages via POST /sse
6. Connection closed by client or server shutdown
```

---

## 5. Terminal Engine

### 5.1 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   TERMINAL ARCHITECTURE                   │
│                                                           │
│  ┌─────────────────────────────────────────────┐         │
│  │           TerminalCompose (Compose UI)       │         │
│  │  ┌──────────┐  ┌────────────┐  ┌─────────┐  │         │
│  │  │Terminal   │  │Terminal    │  │Virtual  │  │         │
│  │  │Screen     │  │InputField  │  │Keys     │  │         │
│  │  └─────┬─────┘  └─────┬──────┘  └────┬────┘  │         │
│  │        │              │              │       │         │
│  └────────┼──────────────┼──────────────┼───────┘         │
│           │              │              │                 │
│           ▼              ▼              ▼                 │
│  ┌─────────────────────────────────────────────┐         │
│  │           TerminalBackEnd                    │         │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────┐  │         │
│  │  │Terminal  │  │Session   │  │JNI Bridge │  │         │
│  │  │Buffer    │  │Manager   │  │(native)   │  │         │
│  │  └──────────┘  └──────────┘  └─────┬─────┘  │         │
│  └────────────────────────────────────┼─────────┘         │
│                                       │                  │
│  ┌────────────────────────────────────┼─────────┐         │
│  │        Termux JNI Layer            │         │         │
│  │  ┌──────────┐  ┌────────────────┐   │         │        │
│  │  │Terminal   │  │JNI            │   │         │        │
│  │  │Emulation  │  │(termux.so)    │   │         │        │
│  │  └──────────┘  └───────┬────────┘   │         │        │
│  └────────────────────────┼────────────┘         │
│                           │                      │
│                   ┌───────▼────────┐             │
│                   │  Linux Process  │            │
│                   │  (bash, zsh)    │            │
│                   └────────────────┘             │
└─────────────────────────────────────────────────┘
```

### 5.2 Key Components

#### 5.2.1 TerminalBackEnd
- **File**: `core/main/src/main/java/com/rk/terminal/TerminalBackEnd.kt`
- **Role**: Core terminal backend — manages terminal emulation, session, I/O
- **Responsibilities**:
  - ANSI escape sequence parsing
  - Screen buffer management (rows × columns of cells)
  - Input/output to/from terminal process
  - Cursor tracking, scrollback buffer
- **Threading**: Reading from process output (background thread), writing to process input (any thread)
- **State**: TerminalBuffer (character grid), cursor position, scroll position, color attributes

#### 5.2.2 TerminalScreen
- **File**: `core/main/src/main/java/com/rk/terminal/TerminalScreen.kt`
- **Role**: Compose UI for rendering the terminal
- **Implementation**: `Canvas` composable rendering character cells with foreground/background colors
- **Features**: Font rendering, color schemes, touch input, scroll, selection
- **Rendering**: Re-renders full buffer on every state change (performance issue — see BUG-39)

#### 5.2.3 MkSession
- **File**: `core/main/src/main/java/com/rk/terminal/MkSession.kt`
- **Role**: Creates new terminal sessions — sets up the process, pseudo-terminal, environment
- **Process**: Creates a subprocess with `ProcessBuilder`, allocates PTY via JNI
- **Environment**: Sets up `PATH`, `HOME`, `TERM`, `ANDROID_ROOT` etc.

#### 5.2.4 MkRootfs
- **File**: `core/main/src/main/java/com/rk/terminal/MkRootfs.kt`
- **Role**: Manages the root filesystem extraction for the terminal environment
- **Function**: Extracts pre-built rootfs archive to app data directory
- **Files**: Sets up `/system/bin`, libraries, busybox symlinks

#### 5.2.5 SessionService
- **File**: `core/main/src/main/java/com/rk/terminal/SessionService.kt`
- **Role**: Manages all terminal sessions for the app
- **State**: `ConcurrentHashMap<String, TerminalSession>` of active sessions
- **Operations**: Create, destroy, list, get session by ID

#### 5.2.6 TerminalFiles
- **File**: `core/main/src/main/java/com/rk/terminal/TerminalFiles.kt`
- **Role**: File management for terminal — download/cache rootfs, manage termux files

#### 5.2.7 Data.kt
- **File**: `core/main/src/main/java/com/rk/terminal/Data.kt`
- **Role**: Data models for terminal — `TerminalSession`, `TerminalBuffer`, `CellCharacter`, colors

#### 5.2.8 Virtual Keys (`virtualkeys/`)
- **File**: `core/main/src/main/java/com/rk/terminal/virtualkeys/`
- **Role**: On-screen virtual keyboard for terminal (Ctrl, Alt, Tab, Esc, arrows)

### 5.3 Terminal Data Flow

```
1. User types 'ls\n' in terminal UI
2. TerminalScreen captures key event
3. Event sent to TerminalBackEnd.writeInput('ls\n')
4. TerminalBackEnd writes to PTY master via JNI
5. PTY slave passes input to shell process (bash)
6. Shell executes 'ls' and writes output to PTY
7. PTY master receives output
8. JNI callback: TerminalBackEnd.onOutput(bytes)
9. TerminalBackEnd parses ANSI escape sequences
10. TerminalBackEnd updates TerminalBuffer (characters, colors, cursor)
11. TerminalBackEnd emits buffer state via StateFlow
12. TerminalScreen.StateFlow collector triggers recomposition
13. TerminalScreen.Canvas re-renders visible portion
```

---

## 6. Service Layer

### 6.1 Architecture

```
┌──────────────────────────────────────────────────┐
│                 IdeService (interface)            │
│  Provides unified API for IDE operations          │
└──────────────────────┬───────────────────────────┘
                       │
┌──────────────────────▼───────────────────────────┐
│              IdeServiceImpl (implementation)      │
│  Aggregates all sub-services                      │
│  ┌──────────────┐  ┌──────────────┐              │
│  │ FileService  │  │ EditorService│              │
│  ├──────────────┤  ├──────────────┤              │
│  │ - readFile() │  │ - openFile() │              │
│  │ - writeFile()│  │ - closeFile()│              │
│  │ - deleteFile()│  │ - insertText()│             │
│  │ - listFiles()│  │ - getCursor() │             │
│  └──────────────┘  └──────────────┘              │
│  ┌──────────────┐  ┌──────────────┐              │
│  │ GitService   │  │ LspService   │              │
│  ├──────────────┤  ├──────────────┤              │
│  │ - status()   │  │ - complete() │              │
│  │ - diff()     │  │ - hover()    │              │
│  │ - log()      │  │ - definition()│             │
│  │ - commit()   │  │ - references()│             │
│  └──────────────┘  └──────────────┘              │
│  ┌──────────────┐  ┌──────────────┐              │
│  │TerminalService│  │ProjectService│             │
│  ├──────────────┤  ├──────────────┤              │
│  │ - execute()  │  │ - getInfo()  │              │
│  │ - writeInput()│  │ - getFiles() │             │
│  └──────────────┘  └──────────────┘              │
└──────────────────────────────────────────────────┘
```

### 6.2 Service Descriptions

#### 6.2.1 IdeService / IdeServiceImpl
- **Interface**: `com.rk.ai.service.IdeService`
- **Implementation**: `com.rk.ai.service.IdeServiceImpl`
- **Pattern**: Facade over all sub-services
- **Consumed by**: MCP tools, AI agents, CLI
- **Lifecycle**: Global singleton initialized in `App.onCreate()`
- **Thread safety**: Mutable `var` fields without synchronization (see BUG-30)

#### 6.2.2 FileService
- **Interface**: `com.rk.ai.service.FileService`
- **Operations**: readFile, writeFile, deleteFile, listFiles, fileExists, getFileInfo
- **Path handling**: All paths relative to workspace root
- **Encoding**: Default platform charset (see BUG-31)

#### 6.2.3 EditorService
- **Interface**: `com.rk.ai.service.EditorService`
- **Operations**: openFile, closeFile, getCursorPosition, setCursorPosition, insertText, deleteText, getText, getOpenFiles
- **Integration**: Tightly coupled with MainActivity and TabManager

#### 6.2.4 GitService
- **Interface**: `com.rk.ai.service.GitService`
- **Library**: JGit (Eclipse Git Java implementation)
- **Operations**: status, diff, log, commit, branch, checkout, add
- **Error handling**: Minimal — assumes valid git repo (see BUG-32)

#### 6.2.5 TerminalService (IdeService layer — distinct from SessionService)
- **Interface**: `com.rk.ai.service.TerminalService`
- **Operations**: executeCommand, writeInput, resizeTerminal, getTerminalState
- **Bridges**: Connects MCP terminal tools to actual terminal backend

#### 6.2.6 ProjectService
- **Interface**: `com.rk.ai.service.ProjectService`
- **Operations**: getProjectInfo, getFiles, searchFiles, getProjectStructure
- **Indexing**: One-time scan on project open (see BUG-33)

#### 6.2.7 LspService
- **Interface**: `com.rk.ai.service.LspService`
- **Operations**: complete, hover, definition, references, rename, format, diagnose
- **LSP Client**: Custom implementation — JSON-RPC over stdin/stdout of language server process
- **Lifecycle**: Spawn on first request, keep running, kill on app destroy

### 6.3 *Ops.kt Interfaces

Each service has a companion `*Ops.kt` file defining individual operation interfaces:

```kotlin
// FileOps.kt
interface FileReadOp { suspend fun readFile(path: String): String }
interface FileWriteOp { suspend fun writeFile(path: String, content: String) }

// EditorOps.kt
interface EditorOpenOp { suspend fun openFile(path: String) }
interface EditorInsertOp { suspend fun insertText(path: String, text: String, pos: Position) }
```

These provide fine-grained interface segregation but the sheer number of interfaces (20+) adds complexity without clear benefit, as `IdeServiceImpl` implements them all anyway.

---

## 7. UI Layer

### 7.1 Architecture

```
┌────────────────────────────────────────────────────────┐
│                 MAIN ACTIVITY LAYER                      │
│                                                          │
│  App.kt (Application class)                              │
│    │                                                     │
│    ▼                                                     │
│  MainActivity.kt (single Activity)                       │
│    │                                                     │
│    ▼                                                     │
│  MainContent.kt (root composable)                        │
│    ├─ MainContentHost.kt (host lifecycle)                │
│    ├─ MainRoutes.kt (navigation routes)                  │
│    ├─ TopBar.kt (top toolbar with tabs)                  │
│    ├─ SessionManager.kt (top-level session coordinator)  │
│    ├─ TabManager.kt (tab management)                     │
│    ├─ MainViewModel.kt (shared ViewModel)                │
│    │                                                     │
│    ├─ Editor Area                                        │
│    │   ├─ Editor composable (code editor)                │
│    │   ├─ InlineAgentBar.kt (AI overlay)                 │
│    │   └─ ...                                            │
│    │                                                     │
│    ├─ Terminal Area                                      │
│    │   ├─ TerminalScreen composable                      │
│    │   └─ TerminalCompose                                │
│    │                                                     │
│    ├─ AI Sheet (bottom sheet)                            │
│    │   └─ AiAgentSheet.kt                                │
│    │                                                     │
│    └─ Dialogs/Sheets                                     │
│        ├─ File explorer                                  │
│        ├─ Search dialog                                  │
│        └─ Settings sheet                                 │
└────────────────────────────────────────────────────────┘
```

### 7.2 Key UI Components

#### 7.2.1 MainActivity
- **File**: `core/main/src/main/java/com/rk/activities/main/MainActivity.kt`
- **Role**: Single Activity entry point
- **Pattern**: Hosts Compose content via `setContent { MainContent() }`
- **Lifecycle**: Manages app-level lifecycle events (start/stop MCP server, terminal cleanup)

#### 7.2.2 MainContent / MainContentHost
- **Files**: `MainContent.kt`, `MainContentHost.kt`
- **Role**: Root composable tree, host for all UI
- **Structure**: Column with TopBar + content area (editor/terminal split) + bottom sheet

#### 7.2.3 MainRoutes
- **File**: `MainRoutes.kt`
- **Role**: Navigation routing (editor, terminal, settings)
- **Pattern**: Simple enum-based routing (no Jetpack Navigation)

#### 7.2.4 TopBar
- **File**: `TopBar.kt`
- **Role**: Top toolbar with file tabs, menu actions
- **Features**: Tab list (open files), overflow menu, new file/close buttons

#### 7.2.5 TabManager
- **File**: `TabManager.kt`
- **Role**: Manages open editor tabs
- **State**: List of open tabs, active tab index
- **Integration**: Syncs with EditorService

#### 7.2.6 SessionManager (UI)
- **File**: `SessionManager.kt`
- **Role**: Manages high-level app sessions (editor + terminal pairs)
- **State**: Active sessions, switching between them
- **Note**: Distinct from AiSessionManager and terminal SessionService

#### 7.2.7 MainViewModel
- **File**: `MainViewModel.kt`
- **Role**: Shared ViewModel for main activity state
- **State**: Current route, open tabs, active session

### 7.3 Navigation Structure

```
MainRoutes enum:
  - EDITOR     → Code editor with tab bar
  - TERMINAL   → Full-screen terminal
  - SPLIT      → Editor + Terminal split view
  - SETTINGS   → Settings screen
```

Navigation is state-driven via `MainViewModel.currentRoute: StateFlow<MainRoute>`.

---

## 8. Settings & Configuration

### 8.1 Settings Architecture

- **File**: `core/main/src/main/java/com/rk/settings/Settings.kt`
- **Pattern**: Global `object Settings` with `SharedPreferences` backing
- **Persistence**: `context.getSharedPreferences("xed_settings", Context.MODE_PRIVATE)`
- **Access pattern**: `Settings.getXxx(key, default)` — static access, no injection

### 8.2 Settings Categories

| Category | Keys | Default | Description |
|----------|------|---------|-------------|
| **AI** | `ai_provider`, `ai_api_key`, `ai_endpoint`, `ai_model`, `ai_temperature`, `ai_max_tokens` | gemini, "", "", gemini-2.0-flash, 0.7, 4096 | AI provider settings |
| **Terminal** | `terminal_font_size`, `terminal_color_scheme`, `terminal_cursor_style`, `terminal_max_scrollback` | 12, "dark", "block", 10000 | Terminal appearance |
| **Editor** | `editor_font_size`, `editor_tab_size`, `editor_word_wrap`, `editor_show_line_numbers` | 14, 4, false, true | Editor settings |
| **MCP** | `mcp_port`, `mcp_enabled`, `mcp_discovery_path` | 8080, true, "" | MCP server settings |
| **General** | `theme`, `language`, `auto_save`, `auto_save_interval` | "system", "en", false, 30 | General settings |

### 8.3 Settings Access Pattern

```kotlin
object Settings {
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences("xed_settings", Context.MODE_PRIVATE)
    }
    
    fun getApiKey(): String = prefs.getString("ai_api_key", "") ?: ""
    fun setApiKey(key: String) = prefs.edit().putString("ai_api_key", key).apply()
    // ... 50+ similar getter/setter pairs
}
```

### 8.4 Issues
- **Plaintext storage**: API keys stored without encryption (see BUG-43)
- **Global mutable state**: `object` with mutable `prefs` — not testable
- **No type safety**: Keys are string constants, values are `Any?` with casts
- **No validation**: Values accepted without validation (see BUG-07)
- **No change notifications**: Settings changes are not observable (no listener mechanism)

---

## 9. Build & Dependency System

### 9.1 Build Configuration

- **Root**: `settings.gradle.kts` — includes all modules
- **App module**: `app/build.gradle.kts` — application plugin, dependencies
- **Core modules**: Each has own `build.gradle.kts` with library plugin

### 9.2 Key Dependencies

| Dependency | Version | Used By | Purpose |
|-----------|---------|---------|---------|
| Kotlin | 1.9.x | All | Language |
| Compose BOM | 2024.x | UI modules | Compose UI framework |
| NanoHTTPD | 2.3.x | core:main | Lightweight HTTP server for MCP |
| OkHttp | 4.11.x | core:main | HTTP client for AI provider calls |
| kotlinx.serialization | 1.6.x | core:main | JSON serialization |
| JGit | 6.8.x | core:main | Git operations |
| Coroutines | 1.7.x | All | Async programming |
| AndroidX Lifecycle | 2.7.x | UI modules | ViewModel, Lifecycle |
| Termux JNI | Local | core:terminal-emulator | Native terminal emulation |

### 9.3 Build Variants
- `debug` — Debuggable, with extra logging
- `release` — Optimized, ProGuard enabled (but without rules for reflection — see BUG-52)

### 9.4 Issues
- **No version catalog**: versions hardcoded per module (see BUG-53)
- **No test configuration**: test dependencies not declared
- **No lint baseline**: lint issues unchecked
- **No CI integration for testing**: GitHub Actions only builds, does not test

---

## 10. Data Flow Diagrams

### 10.1 AI Chat Request Flow

```
                    ┌──────────┐
                    │  User    │
                    │  Input   │
                    └────┬─────┘
                         │
                    ┌────▼─────┐
                    │AiAgent   │
                    │Sheet     │
                    └────┬─────┘
                         │ sendMessage(text)
                    ┌────▼─────────┐
                    │AiSession     │
                    │Manager       │
                    │  state=THINK │
                    └────┬─────────┘
                         │ resolveAgent(type)
                    ┌────▼──────────┐
                    │AgentType      │
                    │Registry       │
                    └────┬──────────┘
                         │ GeminiAgent / OpenCodeAgent
                    ┌────▼──────────┐
                    │AgentEnvironment│
                    │Builder         │
                    └────┬──────────┘
                         │ environment context
                    ┌────▼──────┐
                    │ AiAgent   │
                    │ .complete │
                    └────┬──────┘
                         │ HTTP POST to API
                    ┌────▼──────┐
                    │ AI        │
                    │ Provider  │
                    │ (Gemini/  │
                    │ OpenCode) │
                    └────┬──────┘
                         │ response / stream
                    ┌────▼──────────┐
                    │AiSession      │
                    │Manager        │
                    │ state=RESPOND │
                    └────┬──────────┘
                         │ StateFlow emission
                    ┌────▼──────────┐
                    │AiAgentSheet   │
                    │ UI update     │
                    └───────────────┘
```

### 10.2 MCP Tool Execution Flow

```
                    ┌──────────┐
                    │ MCP      │
                    │ Client   │
                    └────┬─────┘
                         │ POST /tools/call
                    ┌────▼─────────┐
                    │IdeBridge     │
                    │Server        │
                    └────┬─────────┘
                         │
                    ┌────▼─────────┐
                    │McpDispatcher │
                    │ .dispatch()  │
                    └────┬─────────┘
                         │ lookup method
                    ┌────▼──────────┐
                    │McpToolRegistry│
                    │ .getTool()    │
                    └────┬──────────┘
                         │ McpTool instance
                    ┌────▼──────────┐
                    │ Tool.execute  │
                    │ (suspend)     │
                    └────┬──────────┘
                         │ call service layer
                    ┌────▼──────────┐
                    │IdeServiceImpl │
                    │ (FileService/ │
                    │  GitService/  │
                    │  etc.)        │
                    └────┬──────────┘
                         │ result
                    ┌────▼──────────┐
                    │McpDispatcher  │
                    │ format JSON-RPC│
                    └────┬──────────┘
                         │ response
                    ┌────▼──────────┐
                    │IdeBridgeServer│
                    │ HTTP response │
                    └────┬──────────┘
                         │
                    ┌────▼─────┐
                    │ MCP      │
                    │ Client   │
                    └──────────┘
```

### 10.3 Terminal I/O Flow

```
                    ┌──────────┐
                    │  User    │
                    │ Input    │
                    └────┬─────┘
                         │ key event
                    ┌────▼──────────┐
                    │TerminalScreen │
                    │ (Compose)     │
                    └────┬──────────┘
                         │ writeInput()
                    ┌────▼───────────┐
                    │TerminalBackEnd │
                    │ JNI.write()    │
                    └────┬───────────┘
                         │
                    ┌────▼───────────┐
                    │ PTY Master (JNI)│
                    └────┬───────────┘
                         │
                    ┌────▼───────────┐
                    │ PTY Slave      │
                    └────┬───────────┘
                         │
                    ┌────▼───────────┐
                    │ Shell Process  │
                    │ (bash)         │
                    └────┬───────────┘
                         │ stdout
                    ┌────▼───────────┐
                    │ PTY Slave      │
                    └────┬───────────┘
                         │
                    ┌────▼───────────┐
                    │ PTY Master (JNI)│
                    └────┬───────────┘
                         │ JNI callback
                    ┌────▼────────────┐
                    │TerminalBackEnd   │
                    │ onOutput(bytes)  │
                    │ → ANSI parse     │
                    │ → buffer update  │
                    └────┬────────────┘
                         │ StateFlow emission
                    ┌────▼──────────┐
                    │TerminalScreen │
                    │ recompose     │
                    └───────────────┘
```

---

## 11. Cross-Cutting Concerns

### 11.1 Threading Model

| Context | Thread | Mechanism | Issues |
|---------|--------|-----------|--------|
| UI rendering | Main thread | Compose composition | Over-render (BUG-39) |
| AI network calls | Dispatchers.IO | Coroutines | runBlocking in dispatcher (BUG-13) |
| MCP server | NanoHTTPD pool | Fixed thread pool | runBlocking (BUG-13), no timeout (BUG-12) |
| Terminal I/O | Background threads | JNI callbacks | No synchronization (BUG-28) |
| File I/O | Caller thread | Synchronous | Blocks calling thread |

### 11.2 Error Handling Patterns

| Layer | Pattern | Quality |
|-------|---------|---------|
| AI | `Result<T>` | Good for success/failure, no typed errors |
| MCP tools | Try-catch → generic error | Poor — all errors look the same (BUG-17, BUG-20) |
| Terminal | Boolean return | Poor — error details lost (BUG-25) |
| Services | Exception propagation | Mixed — sometimes caught, sometimes not |
| UI | Snackbar/Toast | Inconsistent — many failures silent |

### 11.3 Memory Management

| Component | Memory Usage | Cleanup |
|-----------|-------------|---------|
| AI sessions | Growing with chat history | Manual close (BUG-05) |
| Terminal scrollback | Unbounded | None (BUG-27) |
| MCP SSE connections | Per-connection | None (BUG-14) |
| HTTP sessions | Per-session | None (BUG-15) |
| Editor undo history | Unbounded | None (BUG-55) |
| File index | Memory-mapped | Manual refresh |

### 11.4 Security Posture

| Concern | Status | Severity |
|---------|--------|----------|
| API key storage | Plaintext SharedPreferences | CRITICAL (BUG-43) |
| MCP server auth | None | HIGH (BUG-44) |
| Path traversal | Partial check | HIGH (BUG-18) |
| Command injection | Unvalidated shell exec | MEDIUM (BUG-45) |
| TLS for AI calls | Default (no pinning) | MEDIUM (BUG-46) |
| Input validation | Minimal | MEDIUM (BUG-06) |

---

## 12. Architecture Decisions & Trade-offs

### 12.1 Good Decisions

| Decision | Rationale |
|----------|-----------|
| **Single Activity** | Standard Android best practice for Compose apps |
| **NanoHTTPD** | Lightweight, no dependencies, good for embedded HTTP server on mobile |
| **SSE for streaming** | Standard protocol, widely supported, simple implementation |
| **JSON-RPC for MCP** | Well-defined protocol, easy to implement and debug |
| **StateFlow for UI state** | Proper Compose integration, lifecycle-aware |
| **Modular terminal (JNI)** | Terminal emulation is performance-critical; JNI is appropriate |
| **Kotlin Coroutines + Flow** | Natural fit for async operations, Compose integration |

### 12.2 Questionable Decisions

| Decision | Issue | Alternative |
|----------|-------|-------------|
| **No DI framework** | Manual wiring, untestable, tight coupling | Hilt or Koin |
| **Global singletons everywhere** | Hidden dependencies, initialization order | Constructor injection |
| **Monolithic core:main module** | High coupling, slow builds, no boundaries | Split into ai-core, mcp-core, terminal-core, ui-core |
| **`runBlocking` in MCP dispatcher** | Thread starvation, no scalability | Native suspend/coroutine dispatch |
| **Plaintext secrets** | Security risk | EncryptedSharedPreferences |
| **No tests** | Regression risk, no safety net | JUnit 5 + MockK for unit tests |
| **Reflection for MCP tool params** | Performance overhead, ProGuard issues | kotlinx.serialization |
| **Character-by-character SSE** | Network overhead, client parsing cost | Batched output |
| **Full terminal re-render** | CPU waste, battery drain | Dirty-region rendering |

### 12.3 Technical Debt Summary

| Debt Item | Impact | Effort to Fix |
|-----------|--------|---------------|
| No DI | Very High — blocks testing and modularization | Months |
| Monolithic module | High — slows builds, prevents clear boundaries | Weeks |
| runBlocking in dispatcher | High — limits scalability | Days |
| No tests | High — every change is risky | Months |
| Plaintext keys | High — security vulnerability | Hours |
| No auto-save | High — data loss risk | Days |
| MCP auth | High — security vulnerability | Hours |
| No memory management | Medium — crashes under pressure | Days |
| SSE leakage | Medium — resource leak | Hours |
| Terminal lifecycle | Medium — zombie processes | Days |

---

## 13. Refactoring Target Architecture

Based on the bug.md findings and pro.md analysis, the following refactoring plan has been started with new files in their target packages:

### 13.1 New Package Structure (Created)

```
core/main/src/main/java/com/rk/
├── ai/
│   ├── provider/          # NEW: AiProvider abstraction
│   │   ├── AiProvider.kt       # Interface
│   │   ├── OpenAiProvider.kt   # OpenAI-compatible impl
│   │   ├── GeminiProvider.kt   # Gemini impl
│   │   ├── AiErrorHandler.kt   # Typed error handling
│   │   └── ProviderManager.kt  # Provider registry
│   ├── mcp/               # NEW: MCP manager layer
│   │   ├── McpManager.kt      # MCP lifecycle management
│   │   └── McpDashboard.kt    # MCP status UI
│   ├── ui/                # NEW: AI UI components
│   │   ├── AiChatUi.kt        # Reusable chat composable
│   │   ├── CodeBlockRenderer.kt  # Code block rendering
│   │   └── AiSettingsUi.kt    # AI settings UI
│   └── persistence/       # NEW: Conversation persistence
│       └── ConversationStore.kt  # SQLite/room-backed store
├── terminal2/             # NEW: Refactored terminal engine
│   ├── TerminalSessionManager.kt  # Lifecycle-aware sessions
│   ├── AnsiRenderer.kt           # Efficient ANSI rendering
│   └── TerminalCompose.kt        # Compose terminal widget
├── core/
│   ├── performance/       # NEW: Performance monitoring
│   │   └── MemoryGuard.kt      # Memory pressure handler
│   └── diagnostics/       # NEW: Debug/diagnostics
│       ├── DebugConsole.kt     # Developer console
│       └── DiagnosticScreens.kt # Diagnostic UI
└── utils/                 # NEW: Shared utilities
    └── ClipboardUtil.kt        # Clipboard helpers
```

### 13.2 Target Architecture Principles

1. **Provider abstraction**: AI providers are pluggable via `AiProvider` interface
2. **Lifecycle-aware sessions**: Sessions auto-cleanup, immune to config changes
3. **Non-blocking MCP dispatch**: Coroutine-based dispatch without `runBlocking`
4. **Memory-aware**: Respond to `onTrimMemory`, bound all buffers/caches
5. **Testable**: Constructor injection makes mocking possible
6. **Observable settings**: Settings with StateFlow for reactive UI
7. **Secure by default**: Encrypted secrets, MCP auth, path canonicalization

### 13.3 Migration Path

```
Phase 1 (NOW):  bug.md + pro.md generated ✓
                New files created in target packages
Phase 2:       Wire up AiProvider interface → AiCompletionEngine
Phase 3:       Rewrite McpDispatcher → suspend dispatch
Phase 4:       Add MCP auth + encrypted storage
Phase 5:       Terminal lifecycle + memory management
Phase 6:       Conversation persistence
Phase 7:       Unit tests for AI state machine + MCP dispatcher
Phase 8:       DI integration (Hilt)
```

---

## Appendix A: File Index

| File Path | Subsystem | Role |
|-----------|-----------|------|
| `core/main/.../ai/AiCompletionEngine.kt` | AI | Core completion orchestrator |
| `core/main/.../ai/AiConfig.kt` | AI | Configuration data class |
| `core/main/.../ai/session/AiSessionManager.kt` | AI | Session state machine |
| `core/main/.../ai/AgentModelResolver.kt` | AI | Model resolution |
| `core/main/.../ai/AgentCli.kt` | AI | Terminal CLI for AI |
| `core/main/.../ai/InlineAgentBar.kt` | AI | Inline completion bar |
| `core/main/.../ai/AiAgentSheet.kt` | AI | Chat bottom sheet |
| `core/main/.../ai/IdeBridge.kt` | AI | IDE bridge interface |
| `core/main/.../ai/IdeWorkspace.kt` | AI | Workspace abstraction |
| `core/main/.../ai/ProjectConfig.kt` | AI | Project AI config |
| `core/main/.../ai/agents/AiAgent.kt` | AI | Agent interface |
| `core/main/.../ai/agents/GeminiAgent.kt` | AI | Gemini provider |
| `core/main/.../ai/agents/OpenCodeAgent.kt` | AI | OpenCode provider |
| `core/main/.../ai/agents/AgentProfile.kt` | AI | Agent metadata |
| `core/main/.../ai/agents/AgentTypeRegistry.kt` | AI | Agent registration |
| `core/main/.../ai/session/AgentEnvironmentBuilder.kt` | AI | Env context builder |
| `core/main/.../ai/service/IdeService.kt` | Services | Service interface |
| `core/main/.../ai/service/IdeServiceImpl.kt` | Services | Service impl |
| `core/main/.../ai/service/FileService.kt` | Services | File operations |
| `core/main/.../ai/service/EditorService.kt` | Services | Editor operations |
| `core/main/.../ai/service/GitService.kt` | Services | Git operations |
| `core/main/.../ai/service/TerminalService.kt` | Services | Terminal operations via MCP |
| `core/main/.../ai/service/ProjectService.kt` | Services | Project operations |
| `core/main/.../ai/service/LspService.kt` | Services | LSP operations |
| `core/main/.../ai/bridge/McpTool.kt` | MCP | Tool interface |
| `core/main/.../ai/bridge/McpToolRegistry.kt` | MCP | Tool registry |
| `core/main/.../ai/bridge/DiscoveryFileWriter.kt` | MCP | Discovery file |
| `core/main/.../ai/bridge/IdeNotificationSender.kt` | MCP | Notification |
| `core/main/.../ai/bridge/NotificationHelper.kt` | MCP | Notification helper |
| `core/main/.../ai/bridge/server/IdeBridgeServer.kt` | MCP | HTTP server |
| `core/main/.../ai/bridge/server/McpDispatcher.kt` | MCP | Request dispatcher |
| `core/main/.../ai/bridge/server/SseManager.kt` | MCP | SSE connections |
| `core/main/.../ai/bridge/server/HttpSessionTracker.kt` | MCP | Session tracking |
| `core/main/.../ai/bridge/tools/BaseMcpTool.kt` | MCP | Tool base class |
| `core/main/.../ai/bridge/tools/McpToolHelpers.kt` | MCP | Tool utilities |
| `core/main/.../ai/bridge/tools/FileTools.kt` | MCP | File operations |
| `core/main/.../ai/bridge/tools/GitTools.kt` | MCP | Git operations |
| `core/main/.../ai/bridge/tools/TerminalTools.kt` | MCP | Terminal operations |
| `core/main/.../ai/bridge/tools/LspTools.kt` | MCP | LSP operations |
| `core/main/.../ai/bridge/tools/ToolError.kt` | MCP | Error types |
| `core/main/.../terminal/TerminalBackEnd.kt` | Terminal | Core terminal engine |
| `core/main/.../terminal/TerminalScreen.kt` | Terminal | Terminal UI |
| `core/main/.../terminal/SessionService.kt` | Terminal | Session management |
| `core/main/.../terminal/MkSession.kt` | Terminal | Session creation |
| `core/main/.../terminal/MkRootfs.kt` | Terminal | Rootfs extraction |
| `core/main/.../terminal/TerminalFiles.kt` | Terminal | File management |
| `core/main/.../terminal/Data.kt` | Terminal | Data models |
| `core/main/.../activities/main/MainActivity.kt` | UI | Main activity |
| `core/main/.../activities/main/MainContent.kt` | UI | Root composable |
| `core/main/.../activities/main/MainContentHost.kt` | UI | Host lifecycle |
| `core/main/.../activities/main/MainRoutes.kt` | UI | Navigation |
| `core/main/.../activities/main/MainViewModel.kt` | UI | Shared ViewModel |
| `core/main/.../activities/main/TopBar.kt` | UI | Top toolbar |
| `core/main/.../activities/main/TabManager.kt` | UI | Tab management |
| `core/main/.../activities/main/SessionManager.kt` | UI | Session manager |
| `core/main/.../settings/Settings.kt` | Settings | Global settings |

---

*End of Architecture Documentation — 13 sections covering the complete Xed-Editor codebase*
