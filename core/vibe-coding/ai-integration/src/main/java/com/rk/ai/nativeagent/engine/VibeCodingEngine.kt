@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.rk.ai.agent.AILoggingManager
import com.rk.ai.agent.AppEventBus
import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.events.SessionTodo
import com.rk.ai.agent.events.SessionTodoStatus
import com.rk.ai.agent.events.VibeCodingEvent
import com.rk.ai.agent.events.VibeCodingEventBus
import com.rk.ai.agent.files.FilesManager
import com.rk.ai.agent.files.SkillManager
import com.rk.ai.agent.agents.AgentResult
import com.rk.ai.agent.hooks.HookContext
import com.rk.ai.agent.hooks.HookEvent
import com.rk.ai.agent.hooks.HookManager
import com.rk.ai.agent.hooks.HookResult
import com.rk.ai.agent.hooks.SecurityHook
import com.rk.ai.agent.tools.LocalTools
import com.rk.ai.agent.tools.VibeCodingSystemTools
import com.rk.ai.agent.tools.VibeCodingToolRegistry
import com.rk.ai.agent.tools.createSearchTools
import com.rk.ai.agent.tools.createSkillTools
import com.rk.ai.agent.transformers.Base64ImageToLocalFileTransformer
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.PlaceholderTransformer
import com.rk.ai.agent.transformers.PromptInjectionTransformer
import com.rk.ai.agent.transformers.RegexOutputTransformer
import com.rk.ai.agent.transformers.TimeReminderTransformer
import com.rk.ai.agent.transformers.TransformerContext
import com.rk.ai.core.AppScope
import com.rk.ai.mcp.FileManager
import com.rk.ai.mcp.McpManager
import com.rk.ai.models.InputSchema
import com.rk.ai.models.McpTool
import com.rk.ai.models.Tool
import com.rk.ai.core.MessageRole
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.models.toMessageNode
import com.rk.ai.persistence.db.AppDatabase
import com.rk.ai.persistence.db.fts.MessageFtsManager
import com.rk.ai.persistence.repo.ConversationRepository
import com.rk.ai.persistence.repo.FilesRepository
import com.rk.ai.persistence.repo.MemoryRepository
import com.rk.ai.persistence.settings.SettingsStore
import com.rk.ai.persistence.settings.findModelById
import com.rk.ai.persistence.settings.getCurrentAssistant
import com.rk.ai.providers.ProviderManager
import com.rk.ai.service.IdeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private const val TAG = "VibeCodingEngine"
private val defaultJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

private fun buildOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.MINUTES)
    .build()

private fun buildDatabase(context: Context): AppDatabase =
    Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "vibecoding_db",
    ).fallbackToDestructiveMigration().build()

class VibeCodingEngine(
    private val context: Context,
    val ideService: IdeService,
    scope: CoroutineScope? = null,
    private val json: Json = defaultJson,
    okHttpClient: OkHttpClient = buildOkHttpClient(),
) {
    val suggestionsFlow: MutableStateFlow<List<kotlinx.serialization.json.JsonObject>> = MutableStateFlow(emptyList())
    private val engineScope: CoroutineScope =
        scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = AppScope()

    val database = buildDatabase(context)
    private val memoryRepo = MemoryRepository(database.memoryDao())
    private val conversationRepo = ConversationRepository(
        conversationDAO = database.conversationDao(),
        messageNodeDAO = database.messageNodeDao(),
        favoriteDAO = database.favoriteDao(),
        database = database,
        messageFtsManager = MessageFtsManager(database),
    )
    private val filesRepo = FilesRepository(database.managedFileDao())

    val providerManager = ProviderManager(okHttpClient, context)
    val settingsStore = SettingsStore(context, appScope)

    private val eventBus = AppEventBus()
    private val filesManager = FilesManager(context, filesRepo, appScope)
    private val mcpManager = McpManager(settingsStore, appScope, VibeCodingFileManager(context))
    private val skillManager = SkillManager(context, settingsStore)
    private val localTools = LocalTools(context, eventBus)

    val vibeEventBus = VibeCodingEventBus()

    val generationHandler = GenerationHandler(
        context = context,
        providerManager = providerManager,
        json = json,
        memoryRepo = memoryRepo,
        conversationRepo = conversationRepo,
        aiLoggingManager = AILoggingManager(),
    )

    val toolRegistry = VibeCodingToolRegistry(ideService, context)
    val hookManager = HookManager()

    private val storedAutoRespondRules = mutableListOf<com.rk.ai.nativeagent.engine.PermissionAutoRespondRule>(
        PermissionAutoRespondRule(toolPattern = "readFile", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow reading files"),
        PermissionAutoRespondRule(toolPattern = "glob", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow finding files"),
        PermissionAutoRespondRule(toolPattern = "grep", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow searching text"),
        PermissionAutoRespondRule(toolPattern = "gitStatus", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow git status"),
        PermissionAutoRespondRule(toolPattern = "gitDiff", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow git diff"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*rm *", action = PermissionAction.DENY, description = "Deny file removal commands"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*git *", action = PermissionAction.ALLOW, description = "Allow Git terminal commands"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*", action = PermissionAction.ASK, description = "Ask for other shell commands"),
        PermissionAutoRespondRule(toolPattern = "*", argPattern = "*", action = PermissionAction.ASK, description = "Ask for all other tools")
    )
    private val storedCommandCatalog = mutableListOf<com.rk.ai.nativeagent.engine.CommandCatalogEntry>()

    private var sessionCounter = 0L

    private val _state = MutableStateFlow(VibeCodingState())
    val state: StateFlow<VibeCodingState> = _state.asStateFlow()

    init {
        _state.value = _state.value.copy(
            permissionAutoRespondRules = storedAutoRespondRules.toList(),
        )
        val securityHook = SecurityHook { severity, message, toolName, filePath ->
            val alert = SecurityAlert(severity, message, toolName, filePath)
            addSecurityAlert(alert)
            engineScope.launch {
                vibeEventBus.emit(VibeCodingEvent.SecurityAlert(
                    severity = severity,
                    message = message,
                    toolName = toolName,
                    filePath = filePath,
                ))
            }
        }
        hookManager.register(HookEvent.BEFORE_FILE_WRITE, securityHook)
        hookManager.register(HookEvent.BEFORE_FILE_EDIT, securityHook)

        toolRegistry.onAgentResult = { name, result ->
            val status = when (result) {
                is AgentResult.Success -> AgentActivityStatus.COMPLETED
                is AgentResult.Failure -> AgentActivityStatus.FAILED
                is AgentResult.NotAttempted -> AgentActivityStatus.PENDING
            }
            updateAgentActivity(name, status, result)
        }
    }

    suspend fun evaluateHooks(event: HookEvent, context: HookContext): HookResult {
        return hookManager.checkAll(event, context)
    }

    fun addPermissionAutoRespondRule(rule: com.rk.ai.nativeagent.engine.PermissionAutoRespondRule) {
        storedAutoRespondRules.add(rule)
        _state.value = _state.value.copy(
            permissionAutoRespondRules = storedAutoRespondRules.toList(),
        )
    }

    fun removePermissionAutoRespondRule(idOrPattern: String) {
        storedAutoRespondRules.removeAll { it.id == idOrPattern || it.toolPattern == idOrPattern }
        _state.value = _state.value.copy(
            permissionAutoRespondRules = storedAutoRespondRules.toList(),
        )
    }

    fun addCommandToCatalog(entry: com.rk.ai.nativeagent.engine.CommandCatalogEntry) {
        storedCommandCatalog.removeAll { it.id == entry.id }
        storedCommandCatalog.add(entry)
        _state.value = _state.value.copy(
            commandCatalog = storedCommandCatalog.toList(),
        )
    }

    fun removeCommandFromCatalog(id: String) {
        storedCommandCatalog.removeAll { it.id == id }
        _state.value = _state.value.copy(
            commandCatalog = storedCommandCatalog.toList(),
        )
    }

    fun setSessionTodos(sessionId: Uuid, todos: List<SessionTodo>) {
        _state.value = _state.value.copy(todos = todos)
        engineScope.launch {
            vibeEventBus.emit(VibeCodingEvent.TodoUpdated(sessionId, todos))
        }
    }

    private val todowriteTool = Tool(
        name = "todowrite",
        description = "Create and manage a structured task list for the current session. Use this to break down complex tasks into tracked subtasks. Each todo has a description and status (pending/in_progress/completed/cancelled). Call this at the start of multi-step work to create a plan, then update status as you complete each step. Pass an empty array to read the current todos.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("todos") {
                        put("type", "string")
                        put("description", "JSON array of todos. Each item: {\"description\": \"...\", \"status\": \"pending\"}. Options for status: pending, in_progress, completed, cancelled. Example: [{\"description\": \"Read the main file\", \"status\": \"pending\"}, {\"description\": \"Implement the fix\", \"status\": \"pending\"}]")
                    }
                },
                required = listOf("todos"),
            )
        },
        execute = { args ->
            val todosJsonStr = args.asJsonObject["todos"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'todos'"))
            val todosJson = try {
                com.google.gson.JsonParser.parseString(todosJsonStr).asJsonArray
            } catch (e: Exception) {
                return@Tool listOf(UIMessagePart.Text("Error: invalid JSON in 'todos': ${e.message}"))
            }
            val todos = todosJson.mapIndexed { index, item ->
                val obj = item.asJsonObject
                val desc = obj["description"]?.asJsonPrimitive?.asString ?: "Untitled task"
                val statusStr = obj["status"]?.asJsonPrimitive?.asString ?: "pending"
                val status = when (statusStr.lowercase()) {
                    "in_progress" -> SessionTodoStatus.IN_PROGRESS
                    "completed" -> SessionTodoStatus.COMPLETED
                    "cancelled" -> SessionTodoStatus.CANCELLED
                    else -> SessionTodoStatus.PENDING
                }
                val id = obj["id"]?.asJsonPrimitive?.asString ?: "todo-${Uuid.random()}"
                SessionTodo(id = id, description = desc, status = status)
            }

            val currentTodos = _state.value.todos
            val isReadOp = todos.isEmpty()
            val sessionId = _state.value.activeSessionId ?: Uuid.random()

            if (!isReadOp) {
                setSessionTodos(sessionId, todos)
            }

            val displayTodos = if (isReadOp) currentTodos else todos
            val summary = buildString {
                if (isReadOp) {
                    appendLine("Current task plan (${displayTodos.size} items):")
                } else {
                    appendLine("Task plan updated (${displayTodos.size} items):")
                }
                displayTodos.forEachIndexed { i, todo ->
                    val icon = when (todo.status) {
                        SessionTodoStatus.COMPLETED -> "[✓]"
                        SessionTodoStatus.IN_PROGRESS -> "[→]"
                        SessionTodoStatus.CANCELLED -> "[✗]"
                        SessionTodoStatus.PENDING -> "[ ]"
                    }
                    appendLine("  $icon ${i + 1}. ${todo.description}")
                }
                appendLine()
                val completed = displayTodos.count { it.status == SessionTodoStatus.COMPLETED }
                val inProgress = displayTodos.count { it.status == SessionTodoStatus.IN_PROGRESS }
                appendLine("Progress: $completed/${displayTodos.size} completed, $inProgress in progress")
            }
            listOf(UIMessagePart.Text(summary))
        },
    )

    private val planTool = Tool(
        name = "plan",
        description = "Create a structured multi-step execution plan. Call this BEFORE starting complex multi-file tasks. The plan creates a tracked todo list and returns a clear step-by-step breakdown. Each step should be specific and actionable.",
        parameters = {
            InputSchema.Obj(
                properties = buildJsonObject {
                    putJsonObject("goal") { put("type", "string"); put("description", "The overall goal of this plan") }
                    putJsonObject("steps") {
                        put("type", "string")
                        put("description", "JSON array of step strings describing each action. Example: [\"Read the current implementation in src/Foo.kt\", \"Update the Foo class to support the new feature\", \"Add tests for the new functionality\", \"Run the test suite to verify\"]")
                    }
                },
                required = listOf("goal", "steps"),
            )
        },
        execute = { args ->
            val goal = args.asJsonObject["goal"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'goal'"))
            val stepsJsonStr = args.asJsonObject["steps"]?.asJsonPrimitive?.asString
                ?: return@Tool listOf(UIMessagePart.Text("Error: missing required argument 'steps'"))
            val stepsJson = try {
                com.google.gson.JsonParser.parseString(stepsJsonStr).asJsonArray
            } catch (e: Exception) {
                return@Tool listOf(UIMessagePart.Text("Error: invalid JSON in 'steps': ${e.message}"))
            }

            val todos = stepsJson.mapIndexed { index, step ->
                SessionTodo(id = "step-${Uuid.random()}", description = step.asString, status = SessionTodoStatus.PENDING)
            }

            val sessionId = _state.value.activeSessionId ?: Uuid.random()
            setSessionTodos(sessionId, todos)

            val planText = buildString {
                appendLine("## Plan: $goal")
                appendLine()
                todos.forEachIndexed { i, todo ->
                    appendLine("  [ ] Step ${i + 1}: ${todo.description}")
                }
                appendLine()
                appendLine("---")
                appendLine("Total: ${todos.size} steps")
                appendLine("Start with Step 1. Update progress with `todowrite` after completing each step.")
            }
            listOf(UIMessagePart.Text(planText))
        },
    )

    private fun buildToolList(assistant: com.rk.ai.models.Assistant, settings: com.rk.ai.persistence.settings.Settings): List<Tool> {
        val mcpTools = mcpManager.getAllAvailableTools().map { (serverId, mcpTool) ->
            Tool(
                name = mcpTool.name,
                description = mcpTool.description ?: "",
                parameters = mcpTool.inputSchema?.let { schema ->
                    { schema }
                } ?: { com.rk.ai.models.InputSchema.Obj(kotlinx.serialization.json.buildJsonObject { }) },
                execute = { args ->
                    val argsStr = try {
                        com.google.gson.Gson().toJson(args)
                    } catch (_: Exception) {
                        args.toString()
                    }
                    val kotlinxArgs = json.parseToJsonElement(argsStr).jsonObject
                    mcpManager.callTool(serverId, mcpTool.name, kotlinxArgs)
                },
            )
        }
        val baseTools = buildList {
            addAll(toolRegistry.withMcpTools(mcpTools))
            addAll(localTools.getTools(assistant.localTools))
            if (settings.enableWebSearch) addAll(createSearchTools(settings))
            addAll(createSkillTools(
                enabledSkills = assistant.enabledSkills,
                allSkills = skillManager.listSkills(),
                skillManager = skillManager,
            ))
            add(todowriteTool)
            add(planTool)
        }

        return baseTools.map { tool ->
            val matchingRules = _state.value.permissionAutoRespondRules.filter {
                _state.value.isToolMatchedByRule(it, tool.name)
            }
            val staticRule = matchingRules.lastOrNull { it.argPattern == "*" }
            val needsApprovalDefault = when (staticRule?.action) {
                PermissionAction.ALLOW -> false
                PermissionAction.DENY -> false
                PermissionAction.ASK -> true
                null -> tool.needsApproval
            }

            val originalExecute = tool.execute
            val wrappedExecute: suspend (com.google.gson.JsonElement) -> List<UIMessagePart> = { args ->
                val argsStr = try {
                    com.google.gson.Gson().toJson(args)
                } catch (_: Exception) {
                    args.toString()
                }
                val currentAction = _state.value.shouldAutoRespondPermission(tool.name, argsStr)
                if (currentAction == PermissionAction.DENY) {
                    listOf(UIMessagePart.Text("Tool '${tool.name}' execution denied by permission rule."))
                } else {
                    originalExecute(args)
                }
            }

            tool.copy(
                needsApproval = needsApprovalDefault,
                execute = wrappedExecute
            )
        }
    }

    fun dispose() {
        currentJob?.cancel()
        currentJob = null
        database.close()
    }

    fun getCurrentAssistantId(): Uuid {
        val settings = settingsStore.settingsFlow.value
        return runCatching { settings.getCurrentAssistant().id }.getOrElse {
            Uuid.parse("0950e2dc-9bd5-4801-afa3-aa887aa36b4e")
        }
    }

    fun openFileInEditor(path: String) {
        ideService.openFile(java.io.File(path))
    }

    fun trackAgentActivity(activity: AgentActivity) {
        _state.value = _state.value.copy(
            agentActivities = _state.value.agentActivities + activity,
        )
    }

    fun updateAgentActivity(agentName: String, status: AgentActivityStatus, result: AgentResult? = null) {
        val activities = _state.value.agentActivities.toMutableList()
        val idx = activities.indexOfLast { it.agentName == agentName && it.status == AgentActivityStatus.RUNNING }
        if (idx >= 0) {
            activities[idx] = activities[idx].copy(
                status = status,
                result = result,
                completedAt = if (status == AgentActivityStatus.COMPLETED || status == AgentActivityStatus.FAILED)
                    System.currentTimeMillis() else null,
            )
            _state.value = _state.value.copy(agentActivities = activities)
        }
    }

    fun addSecurityAlert(alert: SecurityAlert) {
        _state.value = _state.value.copy(
            securityAlerts = _state.value.securityAlerts + alert,
        )
    }

    fun clearSecurityAlerts() {
        _state.value = _state.value.copy(securityAlerts = emptyList())
    }

    private var currentJob: Job? = null

    private inner class SystemPromptTransformer : InputMessageTransformer {
        private var injected = false

        override suspend fun transform(
            ctx: TransformerContext,
            messages: List<UIMessage>,
        ): List<UIMessage> {
            if (injected || messages.isEmpty()) return messages
            injected = true

            val workspaceContext = buildString {
                appendLine(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)
                appendLine()
                appendLine("## Current Workspace Context")
                appendLine()

                try {
                    val workspacePath = ideService.getPrimaryWorkspacePath()
                    appendLine("Workspace: $workspacePath")
                    appendLine()

                    val projectConfig = ideService.getProjectConfig(workspacePath)
                    val language = projectConfig["language"]?.asString
                    val buildSystem = projectConfig["buildSystem"]?.asString
                    if (language != null) appendLine("Language: $language")
                    if (buildSystem != null) appendLine("Build System: $buildSystem")

                    appendLine()
                    val gitStatus = ideService.getGitStatus(workspacePath)
                    val branch = gitStatus["branch"]?.asString ?: "unknown"
                    val changes = gitStatus["changes"]?.asJsonArray?.size() ?: 0
                    appendLine("Git Branch: $branch ($changes uncommitted changes)")
                    appendLine()

                    val openFiles = ideService.getOpenFiles()
                    if (openFiles.isNotEmpty()) {
                        appendLine("Open Files:")
                        openFiles.forEach { f ->
                            val path = f["path"]?.asString ?: f["filePath"]?.asString ?: ""
                            appendLine("  - $path")
                        }
                        appendLine()
                    }

                    val activeFile = ideService.getActiveFile()
                    if (activeFile != null) {
                        val activePath = activeFile["path"]?.asString ?: activeFile["filePath"]?.asString ?: ""
                        appendLine("Active File: $activePath")
                        appendLine()
                    }
                } catch (e: Exception) {
                    appendLine("(Workspace context unavailable: ${e.message})")
                }

                appendLine("Use the available tools to read files, search code, and make changes.")
            }

            return listOf(UIMessage.system(workspaceContext.toString())) + messages
        }

        fun reset() { injected = false }
    }

    private val systemPromptTransformer = SystemPromptTransformer()

    val messages: List<UIMessage> get() = _state.value.messages
    val isProcessing: Boolean get() = _state.value.isProcessing

    fun createBranchSession(parentSessionId: Uuid, title: String = "Branch"): Uuid {
        val newId = Uuid.random()
        val node = SessionNode(
            id = newId,
            parentId = parentSessionId,
            title = title,
        )
        _state.value = _state.value.copy(
            sessionTree = _state.value.sessionTree + node,
            activeSessionId = newId,
            parentSessionId = parentSessionId,
        )
        engineScope.launch {
            vibeEventBus.emit(VibeCodingEvent.SessionCreated(newId, parentSessionId))
        }
        return newId
    }

    fun switchToSession(sessionId: Uuid) {
        val node = _state.value.sessionById[sessionId] ?: return
        _state.value = _state.value.copy(
            messages = node.messages,
            activeSessionId = sessionId,
            parentSessionId = node.parentId,
        )
    }

    private fun saveCurrentSessionMessages() {
        val sessionId = _state.value.activeSessionId ?: return
        val tree = _state.value.sessionTree.toMutableList()
        val idx = tree.indexOfFirst { it.id == sessionId }
        if (idx >= 0) {
            tree[idx] = tree[idx].copy(messages = _state.value.messages)
            _state.value = _state.value.copy(sessionTree = tree)
        }
    }

    fun sendMessage(text: String, extraParts: List<UIMessagePart> = emptyList()) {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            val trimmed = text.trim()

            // Ensure a session exists
            val sessionId = _state.value.activeSessionId ?: Uuid.random()
            if (_state.value.activeSessionId == null) {
                val node = SessionNode(id = sessionId, title = trimmed.take(80))
                _state.value = _state.value.copy(
                    sessionTree = _state.value.sessionTree + node,
                    activeSessionId = sessionId,
                )
            }

            _state.value = _state.value.copy(isProcessing = true, error = null)
            _state.value = _state.value.copy(
                messages = _state.value.messages + UIMessage(
                    role = MessageRole.USER,
                    parts = listOf(UIMessagePart.Text(trimmed)) + extraParts,
                ),
            )

            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationStarted) }

            val settings = settingsStore.settingsFlow.value
            val model = settings.findModelById(settings.chatModelId)
            if (model == null) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "No model selected. Configure a provider and model in VibeCoding settings.",
                )
                engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationError) }
                return@launch
            }

            val assistant = settings.getCurrentAssistant()

            val enhancedTools = buildToolList(assistant, settings)

            val memories = if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) {
                    MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    assistant.id.toString()
                }
                memoryRepo.getMemoriesOfAssistant(memoryAssistantId)
            } else null

            val inputTransformers = listOfNotNull(
                systemPromptTransformer,
                PlaceholderTransformer,
                if (assistant.enableTimeReminder) TimeReminderTransformer else null,
                PromptInjectionTransformer,
            )

            val outputTransformers = listOfNotNull(
                RegexOutputTransformer,
                Base64ImageToLocalFileTransformer.also { it.filesManager = filesManager },
            )

            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = assistant,
                    memories = memories,
                    tools = enhancedTools,
                    inputTransformers = inputTransformers,
                    outputTransformers = outputTransformers,
                ).collect { chunk ->
                    when (chunk) {
                        is GenerationChunk.Messages -> {
                            _state.value = _state.value.copy(messages = chunk.messages)
                            saveCurrentSessionMessages()
                        }
                        is GenerationChunk.CompactionNeeded -> {
                            _state.value = _state.value.copy(compactionReason = chunk.reason)
                        }
                    }
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Generation failed",
                )
                engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationError) }
                return@launch
            }

            saveConversation()
            saveCurrentSessionMessages()

            if (checkAndAutoRespondPermissions()) {
                resumeGeneration()
            } else {
                _state.value = _state.value.copy(isProcessing = false)
                engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationFinished) }
            }
        }
    }

    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(isProcessing = false)
    }

    fun clearConversation() {
        _state.value = VibeCodingState(
            commandCatalog = storedCommandCatalog.toList(),
            permissionAutoRespondRules = storedAutoRespondRules.toList(),
        )
        systemPromptTransformer.reset()
    }

    fun loadConversation(conversation: com.rk.ai.models.Conversation) {
        engineScope.launch(Dispatchers.IO) {
            try {
                val loaded = conversationRepo.getConversationById(conversation.id)
                if (loaded != null) {
                    val messages = loaded.currentMessages
                    _state.value = _state.value.copy(
                        messages = messages,
                        currentConversationId = loaded.id,
                        error = null,
                    )
                    saveCurrentSessionMessages()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = "Failed to load conversation: ${e.message}",
                )
            }
        }
    }

    private suspend fun saveConversation() {
        try {
            val messages = _state.value.messages
            if (messages.isEmpty()) return

            val existingId = _state.value.currentConversationId
            val convId = existingId ?: Uuid.random()
            val assistantId = getCurrentAssistantId()

            val title = messages.firstOrNull { it.role == MessageRole.USER }
                ?.toText()?.take(100)?.trim() ?: "VibeCoding"

            val conversation = com.rk.ai.models.Conversation(
                id = convId,
                assistantId = assistantId,
                title = title,
                messageNodes = messages.map { msg -> msg.toMessageNode() },
            )

            if (existingId != null && conversationRepo.existsConversationById(convId)) {
                conversationRepo.updateConversation(conversation)
            } else {
                conversationRepo.insertConversation(conversation)
            }

            _state.value = _state.value.copy(currentConversationId = convId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save conversation", e)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun approveTool(toolCallId: String) {
        updateToolApproval(toolCallId) {
            com.rk.ai.models.ToolApprovalState.Approved
        }
    }

    fun denyTool(toolCallId: String, reason: String = "") {
        updateToolApproval(toolCallId) {
            com.rk.ai.models.ToolApprovalState.Denied(reason)
        }
    }

    fun answerTool(toolCallId: String, answer: String) {
        updateToolApproval(toolCallId) {
            com.rk.ai.models.ToolApprovalState.Answered(answer)
        }
    }

    private fun updateToolApproval(toolCallId: String, stateFn: () -> com.rk.ai.models.ToolApprovalState) {
        val messages = _state.value.messages.toMutableList()
        val lastIdx = messages.lastIndex
        if (lastIdx < 0) return
        val last = messages[lastIdx]
        val updatedParts = last.parts.map { part ->
            if (part is com.rk.ai.models.UIMessagePart.Tool && part.toolCallId == toolCallId) {
                part.copy(approvalState = stateFn())
            } else part
        }
        messages[lastIdx] = last.copy(parts = updatedParts)
        _state.value = _state.value.copy(messages = messages)
        resumeGeneration()
    }

    private fun resumeGeneration() {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isProcessing = true, error = null)
            val settings = settingsStore.settingsFlow.value
            val model = settings.findModelById(settings.chatModelId)
            val assistant = settings.getCurrentAssistant()
            if (model == null || assistant == null) {
                _state.value = _state.value.copy(isProcessing = false, error = "No model or assistant configured")
                return@launch
            }

            val memories = if (assistant.enableMemory) {
                val memoryAssistantId = if (assistant.useGlobalMemory) {
                    com.rk.ai.persistence.repo.MemoryRepository.GLOBAL_MEMORY_ID
                } else {
                    assistant.id.toString()
                }
                memoryRepo.getMemoriesOfAssistant(memoryAssistantId)
            } else null

            val resumeTools = buildToolList(assistant, settings)

            val transformers = listOfNotNull(
                systemPromptTransformer,
                PlaceholderTransformer,
                if (assistant.enableTimeReminder) TimeReminderTransformer else null,
                PromptInjectionTransformer,
            )

            runCatching {
                generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = assistant,
                    memories = memories,
                    tools = resumeTools,
                    inputTransformers = transformers,
                    outputTransformers = emptyList(),
                ).collect { chunk ->
                     when (chunk) {
                         is GenerationChunk.Messages -> {
                             _state.value = _state.value.copy(messages = chunk.messages)
                             saveCurrentSessionMessages()
                         }
                         is GenerationChunk.CompactionNeeded -> {
                             _state.value = _state.value.copy(compactionReason = chunk.reason)
                         }
                     }
                 }
            }.onFailure { e ->
                _state.value = _state.value.copy(isProcessing = false, error = e.message)
            }
            saveCurrentSessionMessages()

            if (checkAndAutoRespondPermissions()) {
                resumeGeneration()
            } else {
                _state.value = _state.value.copy(isProcessing = false)
            }
        }
    }

    private fun checkAndAutoRespondPermissions(): Boolean {
        val lastMessage = _state.value.messages.lastOrNull() ?: return false
        val tools = lastMessage.getTools().filter { it.isPending }
        if (tools.isEmpty()) return false

        var didChange = false
        for (tool in tools) {
            val action = _state.value.shouldAutoRespondPermission(tool.toolName, tool.input)
            when (action) {
                PermissionAction.ALLOW -> {
                    updateToolApproval(tool.toolCallId) { com.rk.ai.models.ToolApprovalState.Approved }
                    didChange = true
                }
                PermissionAction.DENY -> {
                    updateToolApproval(tool.toolCallId) { com.rk.ai.models.ToolApprovalState.Denied("Auto-denied by permission rule") }
                    didChange = true
                }
                else -> {
                    // ASK or null -> leave as pending
                }
            }
        }
        return didChange
    }


}

private class VibeCodingFileManager(private val context: Context) : FileManager {
    override suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): String {
        val dir = File(context.filesDir, "vibecoding_mcp").also { it.mkdirs() }
        val file = File(dir, "${java.util.UUID.randomUUID()}_$displayName")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    override fun getFile(id: String): File = File(id)
}
