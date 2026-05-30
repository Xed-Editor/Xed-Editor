package com.rk.ai.nativeagent.engine

import android.content.Context
import androidx.room.Room
import com.rk.ai.agent.AILoggingManager
import com.rk.ai.agent.GenerationHandler
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.TransformerContext
import com.rk.ai.models.UIMessage
import com.rk.ai.nativeagent.tools.VibeCodingSystemTools
import com.rk.ai.nativeagent.tools.VibeCodingToolRegistry
import com.rk.ai.persistence.db.AppDatabase
import com.rk.ai.persistence.repo.ConversationRepository
import com.rk.ai.persistence.repo.MemoryRepository
import com.rk.ai.core.AppScope
import com.rk.ai.persistence.settings.SettingsStore
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
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VibeCodingEngine(
    private val context: Context,
    private val ideService: IdeService,
    scope: CoroutineScope? = null,
) {
    private val engineScope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val appScope = AppScope()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .build()

    val providerManager = ProviderManager(okHttpClient, context)
    val settingsStore = SettingsStore(context, appScope)

    private val database = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "vibecoding_db",
    ).fallbackToDestructiveMigration().build()

    private val memoryRepo = MemoryRepository(database.memoryDao())
    private val conversationRepo = ConversationRepository(database.conversationDao())
    private val loggingManager = AILoggingManager()

    val generationHandler = GenerationHandler(
        context = context,
        providerManager = providerManager,
        json = json,
        memoryRepo = memoryRepo,
        conversationRepo = conversationRepo,
        aiLoggingManager = loggingManager,
    )

    val toolRegistry = VibeCodingToolRegistry(ideService, context)

    private var currentJob: Job? = null
    private var systemPromptInjected = false

    private val systemPromptTransformer = object : InputMessageTransformer {
        override suspend fun transform(ctx: TransformerContext, messages: List<UIMessage>): List<UIMessage> {
            if (!systemPromptInjected && messages.isNotEmpty()) {
                systemPromptInjected = true
                val systemMsg = UIMessage.system(VibeCodingSystemTools.SYSTEM_INSTRUCTIONS)
                return listOf(systemMsg) + messages
            }
            return messages
        }
    }

    private val _state = MutableStateFlow(VibeCodingState())
    val state: StateFlow<VibeCodingState> = _state.asStateFlow()

    val messages: List<UIMessage>
        get() = _state.value.messages

    val isProcessing: Boolean
        get() = _state.value.isProcessing

    fun sendMessage(text: String) {
        currentJob?.cancel()
        currentJob = engineScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isProcessing = true, error = null)
            val userMsg = UIMessage.user(text.trim())
            _state.value = _state.value.copy(messages = _state.value.messages + userMsg)

            val settings = settingsStore.settingsFlow.value
            val assistant = settings.getCurrentAssistant()
            val modelId = settings.chatModelId
            val model = settings.findModelById(modelId)
            if (model == null) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = "No model selected. Configure a provider and model in VibeCoding settings.",
                )
                return@launch
            }

            try {
                val flow = generationHandler.generateText(
                    settings = settings,
                    model = model,
                    messages = _state.value.messages,
                    assistant = assistant,
                    tools = toolRegistry.allTools,
                    inputTransformers = listOf(systemPromptTransformer),
                    outputTransformers = emptyList(),
                )

                flow.collect { chunk ->
                    when (chunk) {
                        is GenerationHandler.GenerationChunk.Messages -> {
                            _state.value = _state.value.copy(messages = chunk.messages)
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isProcessing = false,
                    error = e.message ?: "Generation failed",
                )
                return@launch
            }

            _state.value = _state.value.copy(isProcessing = false)
        }
    }

    fun stopGeneration() {
        currentJob?.cancel()
        currentJob = null
        _state.value = _state.value.copy(isProcessing = false)
    }

    fun clearConversation() {
        _state.value = VibeCodingState()
        systemPromptInjected = false
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
