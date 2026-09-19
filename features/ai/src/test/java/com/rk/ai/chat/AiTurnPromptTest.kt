package com.rk.ai.chat

import ai.koog.prompt.message.Message
import ai.koog.prompt.message.MessagePart
import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTurnPromptTest {
    private fun history() =
        listOf(
            AiTurn.User("have a look"),
            AiTurn.Assistant(
                text = "Let me look at the project.",
                toolCalls =
                    listOf(
                        AiToolCall("call-1", "read_file", """{"path":"a.kt"}"""),
                        AiToolCall("call-2", "list_dir", "{}"),
                    ),
            ),
            AiTurn.ToolOutput("call-1", "read_file", "file contents", false),
            AiTurn.ToolOutput("call-2", "list_dir", "a.kt", false),
        )

    @Test
    fun allToolCallsShareOneAssistantMessage() {
        val assistants =
            buildConversationPrompt("sys", history())
                .messages
                .filterIsInstance<Message.Assistant>()

        assertEquals("expected exactly one assistant message", 1, assistants.size)
        assertEquals(
            2,
            assistants
                .single()
                .parts
                .filterIsInstance<MessagePart.Tool.Call>()
                .size,
        )
    }

    @Test
    fun everyToolCallIsAnsweredByAMatchingToolResult() {
        val messages = buildConversationPrompt("sys", history()).messages

        val callIds =
            messages
                .filterIsInstance<Message.Assistant>()
                .flatMap { it.parts.filterIsInstance<MessagePart.Tool.Call>() }
                .map { it.id }

        val resultIds =
            messages
                .flatMap { it.parts.filterIsInstance<MessagePart.Tool.Result>() }
                .map { it.id }

        assertEquals(listOf("call-1", "call-2"), callIds)
        assertEquals(callIds, resultIds)
    }

    @Test
    fun toolResultsFollowTheAssistantMessage() {
        val messages = buildConversationPrompt("sys", history()).messages

        val assistantIndex = messages.indexOfFirst { it is Message.Assistant }
        val firstToolResultIndex =
            messages.indexOfFirst { message ->
                message.parts.any { it is MessagePart.Tool.Result }
            }

        assertTrue("tool results must come after the assistant message", firstToolResultIndex > assistantIndex)
    }

    @Test
    fun textOnlyTurnHasNoToolCalls() {
        val prompt =
            buildConversationPrompt(
                "sys",
                listOf(AiTurn.User("hi"), AiTurn.Assistant("All done.", emptyList())),
            )

        val assistant = prompt.messages.filterIsInstance<Message.Assistant>().single()

        assertEquals(1, assistant.parts.size)
        assertTrue(assistant.parts.single() is MessagePart.Text)
    }

    @Test
    fun emptyAssistantTurnProducesNoMessage() {
        val prompt = buildConversationPrompt("sys", listOf(AiTurn.Assistant("  ", emptyList())))

        assertTrue(prompt.messages.filterIsInstance<Message.Assistant>().isEmpty())
    }
}
