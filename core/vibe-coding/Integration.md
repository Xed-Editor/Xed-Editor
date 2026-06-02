# Rikka AI Core Libraries — Integration Guide

## Overview

This is a modular AI SDK for Android, comprising 8 library modules that provide a complete infrastructure for building AI-powered applications with support for:

- **Multi-provider LLM access** (OpenAI-compatible, Google Gemini, Anthropic Claude)
- **Streaming text generation** with SSE (Server-Sent Events)
- **Tool/function calling** with approval workflows
- **MCP (Model Context Protocol)** client with auto-reconnect
- **Web search** (15+ search providers)
- **Conversation memory** (Room + DataStore persistence)
- **Agent runtime** with transformers, skills, prompts
- **Image generation & editing** (DALL-E, Gemini Imagen)
- **Embedding generation**
- **VibeCoding GUI** — Compose-based multi-panel AI coding assistant with agents, security hooks, command palette, skill browser, plugin manager, permission editor, and project instructions viewer

## Module Architecture

```
┌─────────────────────────────────────────────────────┐
│  agent-runtime      (com.rk.ai.agent)               │
│  GenerationHandler, Transformers, Tools, Logging    │
├─────────────────────────────────────────────────────┤
│  ai-mcp-client      (com.rk.ai.mcp)                 │
│  MCP Client with SSE/HTTP transports                │
├─────────────────────────────────────────────────────┤
│  ai-persistence     (com.rk.ai.persistence)         │
│  Room DB, DataStore, Migrations, Repos              │
├─────────────────────────────────────────────────────┤
│  agent-tools-search (com.rk.ai.tools.search)        │
│  15+ web search providers                           │
├────────────┬────────────┬───────────────────────────┤
│ ai-providers│ ai-models  │ ai-streaming             │
│ Provider    │ Data models│ SSE, JSON, Logging       │
│ interface   │ Conversation│ KeyRoulette, ErrorParser │
│ OpenAI/     │ Message,   │                           │
│ Google/     │ Assistant, │                           │
│ Claude impl │ Tool, MCP  │                           │
├────────────┴────────────┴───────────────────────────┤
│  ai-core              (com.rk.ai.core)              │
│  MessageRole, TokenUsage, ReasoningLevel, Caching   │
└─────────────────────────────────────────────────────┘
```

### Dependency Graph

```
agent-runtime
  → ai-persistence → ai-providers → ai-models → ai-streaming → ai-core
                   → agent-tools-search → ai-models → ai-streaming
  → ai-mcp-client → ai-persistence, ai-models, ai-streaming
```

## Integration Guide

### Prerequisites

- Android `minSdk = 26`
- Java 21+ / Kotlin 2.x
- Gradle with Kotlin DSL + version catalog (`libs`)

### Step 1: Add Modules as Dependencies

In your Android app's `build.gradle.kts`, add dependencies on the modules you need:

```kotlin
dependencies {
    // Core (always required)
    implementation(project(":core:ai-core"))
    implementation(project(":core:ai-streaming"))
    implementation(project(":core:ai-models"))

    // Providers (for LLM access)
    implementation(project(":core:ai-providers"))

    // Persistence (for settings & database)
    implementation(project(":core:ai-persistence"))

    // Agent runtime (for generation pipeline)
    implementation(project(":core:agent-runtime"))

    // MCP client (for external tool servers)
    implementation(project(":core:ai-mcp-client"))

    // Web search
    implementation(project(":core:agent-tools-search"))
}
```

### Step 2: Initialize Core Components

```kotlin
// 1. Create an OkHttpClient (shared across all modules)
val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .build()

// 2. Create AppScope
val appScope = AppScope()

// 3. ProviderManager (registers OpenAI, Google, Claude)
val providerManager = ProviderManager(okHttpClient, applicationContext)

// 4. SettingsStore (DataStore-based persistence)
val settingsStore = SettingsStore(applicationContext, appScope)

// 5. Room Database
val database = Room.databaseBuilder(
    applicationContext,
    AppDatabase::class.java,
    "ai_database"
).build()

// 6. Repositories
val memoryRepo = MemoryRepository(database.memoryDao())
val conversationRepo = ConversationRepository(database.conversationDao())

// 7. AILoggingManager
val loggingManager = AILoggingManager()

// 8. GenerationHandler (the main AI orchestration engine)
val generationHandler = GenerationHandler(
    context = applicationContext,
    providerManager = providerManager,
    json = json, // kotlinx.serialization Json instance
    memoryRepo = memoryRepo,
    conversationRepo = conversationRepo,
    aiLoggingManager = loggingManager,
)
```

### Step 3: Configure Providers

Providers are stored in `SettingsStore` as a list of `ProviderSetting`. Default providers are pre-configured in `DefaultProviders.kt` (OpenAI, Gemini, Claude, DeepSeek, etc.). Users just need to set API keys:

```kotlin
settingsStore.update { settings ->
    settings.copy(
        providers = settings.providers.map { provider ->
            when (provider) {
                is ProviderSetting.OpenAI -> provider.copy(
                    apiKey = "sk-..." // Set your API key
                )
                is ProviderSetting.Google -> provider.copy(
                    apiKey = "AIza..." // Set your Gemini key
                )
                else -> provider
            }
        }
    )
}
```

### Step 4: Send a Chat Message

```kotlin
// Find a model and provider
val settings = settingsStore.settingsFlow.value
val model = settings.findModelById(someModelId) ?: error("Model not found")
val assistant = settings.getCurrentAssistant()

// Prepare messages
val messages = listOf(
    UIMessage.system("You are a helpful assistant."),
    UIMessage.user("Hello, what can you do?")
)

// Generate stream
val flow = generationHandler.generateText(
    settings = settings,
    model = model,
    messages = messages,
    assistant = assistant,
    tools = emptyList(),
)

// Collect results
scope.launch {
    flow.collect { chunk ->
        when (chunk) {
            is GenerationChunk.Messages -> {
                val latestMessage = chunk.messages.last()
                val text = latestMessage.toText()
                // Update UI with text
            }
        }
    }
}
```

### Step 5: Configure a Search Provider

```kotlin
settingsStore.update { settings ->
    settings.copy(
        searchServices = listOf(
            SearchServiceOptions.BingLocalOptions()
            // or: SearchServiceOptions.TavilyOptions(apiKey = "...")
        ),
        searchServiceSelected = 0
    )
}
```

Search tools are automatically injected into the generation pipeline when `settings.enableWebSearch` is true.

### Step 6: Configure MCP Servers

```kotlin
val mcpManager = McpManager(
    settingsStore = settingsStore,
    appScope = appScope,
    fileManager = fileManager, // Your FileManager implementation
)

// Add an MCP server via settings
settingsStore.update { settings ->
    settings.copy(
        mcpServers = settings.mcpServers + McpServerConfig.SseTransportServer(
            url = "https://your-mcp-server.com/sse",
            commonOptions = McpCommonOptions(
                name = "My MCP Server",
                headers = listOf("Authorization" to "Bearer token")
            )
        )
    )
}
```

### Step 7: Use Transformers (Pre/Post Processing)

Transformers modify messages before sending to the API (input) or after receiving (output):

```kotlin
val inputTransformers = listOf<InputMessageTransformer>(
    PlaceholderTransformer(),
    PromptInjectionTransformer(settingsStore, context),
    DocumentAsPromptTransformer(filesManager, context),
)

val outputTransformers = listOf<OutputMessageTransformer>(
    ThinkTagTransformer(),
    Base64ImageToLocalFileTransformer(filesManager),
    OCRTransformer(context),
    RegexOutputTransformer(),
    TemplateTransformer(),
    TimeReminderTransformer(context),
)
```

Pass these to `GenerationHandler.generateText()`.

## Core Concepts

### UIMessage / UIMessagePart

Messages use a **sealed class hierarchy** for parts:
- `UIMessagePart.Text` — text content
- `UIMessagePart.Image` — image (URL, data URI, or base64)
- `UIMessagePart.Video` / `UIMessagePart.Audio` / `UIMessagePart.Document`
- `UIMessagePart.Reasoning` — chain-of-thought / thinking
- `UIMessagePart.Tool` — tool call with input + output (replaces legacy `ToolCall` + `ToolResult`)
- `UIMessagePart.ToolCall` — (deprecated) legacy tool call
- `UIMessagePart.ToolResult` — (deprecated) legacy tool result

### Provider Interface

```kotlin
interface Provider<T : ProviderSetting> {
    suspend fun listModels(setting: T): List<Model>
    suspend fun getBalance(setting: T): String
    suspend fun generateText(setting: T, messages, params): MessageChunk
    suspend fun streamText(setting: T, messages, params): Flow<MessageChunk>
    suspend fun generateEmbedding(setting: T, params): EmbeddingGenerationResult
    suspend fun generateImage(setting, params): ImageGenerationResult
    suspend fun editImage(setting, params): ImageGenerationResult
}
```

Built-in implementations:
- `OpenAIProvider` — OpenAI-compatible APIs (Chat Completions & Response API)
- `GoogleProvider` — Gemini API + Vertex AI
- `ClaudeProvider` — Anthropic Claude API

### Tool System

```kotlin
data class Tool(
    val name: String,
    val description: String,
    val parameters: () -> InputSchema?,
    val systemPrompt: (modelId, messages) -> String,
    val needsApproval: Boolean,
    val execute: suspend (JsonElement) -> List<UIMessagePart>,
)
```

Built-in tools:
- `search_web` / `scrape_web` — web search
- `memory_tool` — create/edit/delete long-term memories
- `use_skill` — load skill instructions
- `get_time_info` — device time
- `clipboard_tool` — read/write clipboard
- `eval_javascript` — JS execution (stub)
- `text_to_speech` — TTS via AppEventBus
- `ask_user` — human-in-the-loop approval

### Key Models

| Model | Description |
|---|---|
| `Assistant` | AI persona with system prompt, temperature, tools, MCP servers, regex, lorebook |
| `Conversation` | Chat session with message nodes, branching, pinned status |
| `UIMessage` | Single message with role + parts + annotations |
| `MessageNode` | Container for message branching (multiple candidates per turn) |
| `MessageChunk` | Streaming chunk with choices + delta + usage |
| `Tool` | Function definition with JSON schema and executor |
| `ProviderSetting` | API credentials, base URL, model list per provider |
| `Model` | AI model definition with modalities, abilities, custom headers |

### Settings (Persistence)

The `Settings` data class (stored in Jetpack DataStore) holds:
- `providers` — list of `ProviderSetting`
- `assistants` — list of `Assistant`
- `mcpServers` — MCP server configurations
- `searchServices` — search provider configs
- `modeInjections` / `lorebooks` — prompt injection rules
- `favoriteModels` — pinned model IDs
- Translation config

### MCP (Model Context Protocol)

- Supports **SSE** and **Streamable HTTP** transports
- Automatic tool syncing on connect
- Exponential backoff reconnection (up to 5 attempts)
- Status tracking via `StateFlow<Map<Uuid, McpStatus>>`
- MCP image results auto-converted to local files

### Search Providers (15+)

Bing, Tavily, Exa, Brave, Jina, SearXNG, Perplexity, Firecrawl, Grok, Bocha, Metaso, Ollama, Zhipu, LinkUp, RikkaHub, Tinyfish, Custom JS

### Reasonning Levels

```kotlin
enum class ReasoningLevel(val budgetTokens: Int, val effort: String) {
    OFF(0, "none"), AUTO(-1, "auto"),
    LOW(1000, "low"), MEDIUM(2000, "medium"),
    HIGH(8000, "high"), XHIGH(16000, "xhigh")
}
```

## AndroidManifest Requirements

Each module declares its own namespace. Your host app needs:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

No special activities or services are required by the libraries.

## ProGuard / R8

Each module includes a `consumer-rules.pro`. Key rules:
- Keep `kotlinx.serialization` serializable classes
- Keep `com.rk.ai.**` models
- Keep `okhttp3` and `ktor` internals

## Sample Integration Flow

```
┌──────────────────────────────────────────────────────────────────┐
│  Your App                                                        │
│                                                                  │
│  1. User types message 👉 UIMessage.user("hello")                │
│  2. Assemble messages (system + history + user)                  │
│  3. transformers transform input messages                        │
│  4. Provider builds API-specific request body                    │
│     (e.g. OpenAI: /chat/completions)                             │
│  5. OkHttp sends request + SSE stream starts                     │
│  6. Flow<MessageChunk> emits deltas                              │
│  7. transformers transform output chunks                         │
│  8. Tool calls detected → execute tools → loop                   │
│  9. Final message emitted as GenerationChunk.Messages            │
│  10. Save conversation via ConversationRepository                │
└──────────────────────────────────────────────────────────────────┘
```

## Key Points for Integrators

1. **All modules are Android library AARs** — No application entry point needed.
2. **OkHttpClient is shared** — Provide one client; all modules use it.
3. **ProviderManager must be initialized** before calling `generateText`.
4. **SettingsStore auto-populates** default providers and assistants on first run.
5. **MCP Manager auto-connects** — It watches the settings flow and manages lifecycle.
6. **Tool approval** uses `ToolApprovalState` (Auto/Pending/Approved/Denied/Answered).
7. **JSON expression engine** (`JsonExpression.kt`) supports path navigation, arithmetic, string concatenation for dynamic config.
8. **Key Roulette** provides LRU-based round-robin or random API key selection.
9. **All I/O is on Dispatchers.IO** — flows are `flowOn(Dispatchers.IO)` automatically.
10. **Error details** are recursively parsed from provider error responses via `parseErrorDetail()`.

---

## VibeCoding Module — OpenCode-Inspired AI Coding Agent

The VibeCoding module (`ai-integration` + `agent-runtime`) provides a full-featured AI coding assistant inspired by OpenCode, with a Jetpack Compose GUI.

### Architecture

```
VibeCodingPanel (Compose UI)
  ├── Toolbar      — model selector, tool buttons (Commands, Skills, Agents, Files,
  │                  History, Rules, Plugins, Perms, Settings, Clear)
  ├── MessageList  — chat messages with markdown rendering, code highlighting, diffs
  ├── VibeCodingInput — text input with attachment support
  ├── StatusBar    — security alerts, agent activity, processing state
  ├── FileTreeSidebar — project file browser
  ├── ConversationSidebar — session history
  └── BottomSheet Panels:
      ├── CommandPaletteSheet — 13+ built-in commands (/init, /review, /commit, etc.)
      ├── SkillBrowserPanel — SKILL.md discovery, toggle on/off
      ├── AgentConfigPanel — agent manager (create/edit/save agents)
      ├── PermissionEditorPanel — visual permission rule editor
      ├── InstructionsEditorPanel — AGENTS.md/CLAUDE.md viewer/editor
      └── PluginManagerPanel — plugin browser and activator

VibeCodingEngine (business logic)
  ├── sendMessage()  → GenerationHandler.generateText()
  ├── resumeGeneration() → tool approval resumption
  ├── HookManager + SecurityHook → guard system (8 dangerous patterns)
  ├── ToolRegistry → 80+ tools (file, editor, search, LSP, git, terminal, etc.)
  ├── AgentRegistry → 4 sub-agents (code-reviewer, bug-hunter, architect, test-generator)
  └── StateFlow<VibeCodingState> → reactive UI state
```

### Key Features

| Feature | Description | OpenCode Equivalent |
|---|---|---|
| **Agent Manager** | Create/edit/save agent personas with custom system prompts, temps, memory | `opencode.json` `agent` config + .md agent files |
| **Command Palette** | 13 built-in commands: init, review, test, commit, push, changelog, spellcheck, translate, summarize, compact, learn, rmslop, issues | `/` slash commands in TUI |
| **Skill Browser** | Discover and toggle SKILL.md files from `.opencode/skills/` | `skill` tool discovery |
| **Plugin Manager** | Browse, activate/deactivate built-in and external plugins | `.opencode/plugins/` + npm plugins |
| **Permission Editor** | Visual pattern-based permission rules (allow/ask/deny) with wildcard support | `permission` config field |
| **Instructions Editor** | View/edit AGENTS.md, CLAUDE.md with syntax highlighting | `AGENTS.md` / `CLAUDE.md` |
| **Security Hooks** | 8 dangerous pattern detectors (unsafe deserialization, XSS, SQLi, hardcoded secrets, path traversal, etc.) with CRITICAL/HIGH/MEDIUM severity | Security Guidance plugin + hooks |
| **Sub-Agents** | 4 specialized agents: code-reviewer, bug-hunter, architect, test-generator | opencode `agent` subagent system |
| **Git Workflow** | status, diff, log, branch, commit, push, PR creation | opencode git tools |
| **MCP Support** | SSE/HTTP MCP server connections with auto-reconnect | MCP server config |

### New Files

```
ai-integration/src/main/java/com/rk/ai/nativeagent/ui/components/
├── AgentConfigPanel.kt          — Agent manager GUI
├── CommandPaletteSheet.kt       — Command palette bottom sheet
├── SkillBrowserPanel.kt         — Skill browser/editor
├── PluginManagerPanel.kt        — Plugin manager
├── PermissionEditorPanel.kt     — Permission rule editor
├── InstructionsEditorPanel.kt   — AGENTS.md viewer/editor
├── AgentActivityCard.kt         — Sub-agent status card
├── SecurityAlertBanner.kt       — Security warning banner
├── VibeCodingStatusBar.kt       — Processing/metrics bar
└── WorkflowPhaseIndicator.kt    — Multi-phase workflow progress
```

### Tool Distribution (80+ tools)

| Group | Tools | File |
|---|---|---|
| **File** | readFile, writeFile, createFile, deleteFile, moveFile, listFiles, findFiles, glob, grep, searchFile, head, tail, wc, stat | VibeCodingFileTools.kt |
| **Editor** | getOpenFiles, getActiveFile, getSelection, getSymbolUnderCursor, openFile | VibeCodingEditorTools.kt |
| **Search** | searchCode (regex + plain) | VibeCodingSearchTools.kt |
| **LSP** | getDiagnostics, findDefinitions, findReferences, renameSymbol | VibeCodingLspTools.kt |
| **Git** | gitStatus, gitDiff, gitLog, gitBranch, gitCheckout, gitCommit, gitPush, createPullRequest | VibeCodingGitTools.kt |
| **Terminal** | runCommand | VibeCodingTerminalTools.kt |
| **Project** | getProjectStructure, getProjectConfig, getProjectSummary, getProjectInstructions, getSystemInfo | VibeCodingProjectTools.kt |
| **System** | getClipboard, writeToClipboard, insertAtCursor, getIdeInfo, getEnvironment | VibeCodingSystemTools.kt |
| **Diff** | openDiff, rejectDiff | VibeCodingDiffTools.kt |
| **Web** | webSearch, webFetch, webResearch, webDownload | VibeCodingWebTools.kt |
| **GitHub** | githubRepoInfo, githubReadme, githubFileFetch, githubSearchCode | VibeCodingGitHubTools.kt |
| **Packages** | mavenSearch, npmSearch, pipSearch | VibeCodingPackageTools.kt |
| **Agents** | listAgents, delegateTask | AgentRegistry.kt |

### Security Hook Patterns

| Pattern | Severity |
|---|---|
| Unsafe YAML deserialization (`yaml.load`) | HIGH |
| Unsafe pickle deserialization | CRITICAL |
| XSS (innerHTML, dangerouslySetInnerHTML) | HIGH |
| Hardcoded credentials | CRITICAL |
| Dynamic code execution (exec, eval) | HIGH |
| Destructive commands (rm -rf) | CRITICAL |
| SQL injection | HIGH |
| Path traversal (../) | MEDIUM |

### Integration with VibeCodingEngine

```kotlin
// Initialize the engine
val engine = VibeCodingEngine(
    context = applicationContext,
    ideService = yourIdeService,
)

// Hooks auto-register in init block
// Security alerts flow to UI via state.securityAlerts

// Agent results update state.agentActivities automatically
// via toolRegistry.onAgentResult callback

// Dispose when done
engine.dispose()
```
