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

    private val storedAutoRespondRules = mutableListOf<com.rk.ai.nativeagent.engine.PermissionAutoRespondRule>()
    private val storedCommandCatalog = mutableListOf<com.rk.ai.nativeagent.engine.CommandCatalogEntry>()

    private var sessionCounter = 0L

    init {
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

    fun removePermissionAutoRespondRule(pattern: String) {
        storedAutoRespondRules.removeAll { it.pattern == pattern }
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
        return buildList {
            addAll(toolRegistry.withMcpTools(mcpTools))
            addAll(localTools.getTools(assistant.localTools))
            if (settings.enableWebSearch) addAll(createSearchTools(settings))
            addAll(createSkillTools(
                enabledSkills = assistant.enabledSkills,
                allSkills = skillManager.listSkills(),
                skillManager = skillManager,
            ))
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

    private val _state = MutableStateFlow(VibeCodingState())
    val state: StateFlow<VibeCodingState> = _state.asStateFlow()

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
        val node = _state.value.sessionTree.find { it.id == sessionId } ?: return
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

            // Auto-respond permission check
            val pendingToolCalls = _state.value.messages.lastOrNull()
                ?.getTools()
                ?.filter { it.approvalState is com.rk.ai.models.ToolApprovalState.Auto }
                .orEmpty()
            for (tool in pendingToolCalls) {
                val rule = _state.value.shouldAutoRespondPermission(tool.toolName)
                when (rule) {
                    com.rk.ai.nativeagent.engine.PermissionAction.ALLOW -> approveTool(tool.toolCallId)
                    com.rk.ai.nativeagent.engine.PermissionAction.DENY -> denyTool(tool.toolCallId, "Auto-denied by permission rule")
                    else -> {} // ASK -> leave as Pending for user
                }
            }

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
                        else -> {}
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
            _state.value = _state.value.copy(isProcessing = false)
            saveCurrentSessionMessages()
            engineScope.launch { vibeEventBus.emit(VibeCodingEvent.GenerationFinished) }
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
                    }
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(isProcessing = false, error = e.message)
            }
            _state.value = _state.value.copy(isProcessing = false)
            saveCurrentSessionMessages()
        }
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
