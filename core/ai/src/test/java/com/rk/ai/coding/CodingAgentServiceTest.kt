@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.rk.ai.coding

import com.rk.ai.agent.GenerationChunk
import com.rk.ai.agent.transformers.InputMessageTransformer
import com.rk.ai.agent.transformers.OutputMessageTransformer
import com.rk.ai.coding.context.ContextBuilder
import com.rk.ai.coding.fakes.FakeIdeService
import com.rk.ai.coding.tools.ToolRegistry
import com.rk.ai.core.MessageRole
import com.rk.ai.models.Assistant
import com.rk.ai.models.AssistantMemory
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessage
import com.rk.ai.models.UIMessagePart
import com.rk.ai.persistence.settings.Settings
import com.rk.ai.providers.Model
import com.rk.ai.providers.ProviderSetting
import java.io.File
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class CodingAgentServiceTest {
    @Test
    fun streamsGenerationThroughRunnerAndInjectsContext() = runBlocking {
        val fakeIde = FakeIdeService(File("/tmp/native-service"))
        fakeIde.putFile("src/Main.kt", "class Main")
        val runner = FakeRunner()
        val service = CodingAgentService(runner, fakeIde, ContextBuilder(fakeIde), ToolRegistry())
        val model = Model(modelId = "test-model", displayName = "Test")
        val assistant = Assistant(systemPrompt = "Base prompt")
        val settings = settingsFor(model, assistant)

        val events = service.stream(
            CodingAgentRequest(
                userText = "explain current file",
                settings = settings,
                model = model,
                assistant = assistant,
            )
        ).toList()

        assertTrue(events.any { it is CodingAgentEvent.SessionStarted })
        assertTrue(events.any { it is CodingAgentEvent.Messages })
        assertTrue(events.any { it is CodingAgentEvent.Completed })
        assertNotNull(runner.lastSystemPrompt)
        assertTrue(runner.lastSystemPrompt?.contains("Workspace context") == true)
    }

    @Test
    fun cancellationMarksSessionCancelled() = runBlocking {
        val fakeIde = FakeIdeService(File("/tmp/native-cancel"))
        fakeIde.putFile("src/Main.kt", "class Main")
        val runner = FakeRunner(blockAfterFirst = true)
        val service = CodingAgentService(runner, fakeIde, ContextBuilder(fakeIde), ToolRegistry())
        val model = Model(modelId = "test-model", displayName = "Test")
        val assistant = Assistant(systemPrompt = "Base prompt")
        val settings = settingsFor(model, assistant)
        var sessionId: String? = null

        val job = launch {
            service.stream(
                CodingAgentRequest(
                    userText = "start",
                    settings = settings,
                    model = model,
                    assistant = assistant,
                )
            ).collect { event ->
                if (event is CodingAgentEvent.SessionStarted) {
                    sessionId = event.sessionId
                }
                if (event is CodingAgentEvent.Messages && sessionId != null) {
                    service.cancelSession(sessionId!!)
                }
            }
        }

        withTimeout(1_000) { job.join() }
        assertEquals(CodingAgentSessionStatus.Cancelled, service.getSession(sessionId!!)?.status)
    }

    private fun settingsFor(model: Model, assistant: Assistant): Settings = Settings(
        chatModelId = model.id,
        assistantId = assistant.id,
        providers = listOf(ProviderSetting.OpenAI(models = listOf(model))),
        assistants = listOf(assistant),
    )

    private class FakeRunner(
        private val blockAfterFirst: Boolean = false,
    ) : CodingGenerationRunner {
        var lastSystemPrompt: String? = null

        override fun generateText(
            settings: Settings,
            model: Model,
            messages: List<UIMessage>,
            inputTransformers: List<InputMessageTransformer>,
            outputTransformers: List<OutputMessageTransformer>,
            assistant: Assistant,
            memories: List<AssistantMemory>?,
            tools: List<Tool>,
            maxSteps: Int,
            processingStatus: MutableStateFlow<String?>,
            conversationSystemPrompt: String?,
            conversationModeInjectionIds: Set<Uuid>,
            conversationLorebookIds: Set<Uuid>,
        ): Flow<GenerationChunk> = flow {
            lastSystemPrompt = conversationSystemPrompt
            emit(
                GenerationChunk.Messages(
                    messages + UIMessage(
                        role = MessageRole.ASSISTANT,
                        parts = listOf(UIMessagePart.Text("done")),
                    )
                )
            )
            if (blockAfterFirst) awaitCancellation()
        }
    }
}
