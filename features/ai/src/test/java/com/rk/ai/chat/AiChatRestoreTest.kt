package com.rk.ai.chat

import com.rk.ai.model.AiChatMessage
import com.rk.ai.model.AiChatRole
import com.rk.ai.model.AiTodo
import com.rk.ai.model.AiTurn
import com.rk.ai.model.TodoStatus
import com.rk.ai.model.ToolCallStatus
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The controller's half of persistence: a restored chat has to come back exactly as it was saved,
 * and asking for the state again without touching anything has to be cheap.
 */
class AiChatRestoreTest {
    private val snapshot =
        AiChatSnapshot(
            messages =
                listOf(
                    AiChatMessage(id = 0, role = AiChatRole.User, text = "hello"),
                    AiChatMessage(
                        id = 1,
                        role = AiChatRole.Tool,
                        text = "waiting for approval…",
                        toolName = "write_file",
                        toolArgs = """{"path":"a.kt"}""",
                        toolStatus = ToolCallStatus.AwaitingApproval,
                    ),
                ),
            history = listOf(AiTurn.User("hello")),
            goal = "finish the feature",
            todos = listOf(AiTodo("persist the tab", TodoStatus.InProgress)),
            draft = "unsent thought",
            nextId = 2,
            showReasoning = true,
        )

    @Test
    fun applyingASnapshotReinstatesEveryField() {
        val controller = AiChatController()
        controller.applySnapshot(snapshot)

        // The only field that is not restored verbatim is a tool status that still promised
        // progress: the run that owned it did not survive the process, so it comes back interrupted.
        assertEquals(snapshot.settled().messages, controller.messages.toList())
        assertEquals("finish the feature", controller.goal)
        assertEquals(snapshot.todos, controller.todos)
        assertEquals("unsent thought", controller.draft)
        assertTrue(controller.showReasoning)
        // The transcript that the next request replays is restored as-is; only the tool card status
        // is adjusted for the run that no longer exists.
        assertEquals(listOf(AiTurn.User("hello")), controller.snapshot().history)
        assertEquals(
            ToolCallStatus.Interrupted,
            controller.messages.single { it.toolStatus != null }.toolStatus,
        )
        controller.dispose()
    }

    @Test
    fun applyingASnapshotKeepsMessageIdsUniqueAfterRestore() {
        val controller = AiChatController()
        controller.applySnapshot(snapshot)

        val next = controller.snapshot().nextId
        assertTrue("ids must continue past every restored message", next > snapshot.messages.maxOf { it.id })
        controller.dispose()
    }

    @Test
    fun aRestoredChatEncodesToTheSameBytesAsTheOneThatWasSaved() {
        val controller = AiChatController()
        controller.applySnapshot(snapshot)
        val saved = AiChatPayload.encode(controller.snapshot())

        val reopened = AiChatController()
        val decoded = AiChatPayload.decode(saved)
        reopened.applySnapshot(decoded!!)

        assertEquals(controller.snapshot().messages, reopened.snapshot().messages)
        assertEquals(controller.snapshot().history, reopened.snapshot().history)
        assertEquals(controller.snapshot().draft, reopened.snapshot().draft)
        assertArrayEquals(saved, AiChatPayload.encode(reopened.snapshot()))
        controller.dispose()
        reopened.dispose()
    }

    @Test
    fun anIdleChatEncodesToTheSameBytesWhileAnEditChangesThem() {
        // Session saves happen on every pause, so the encoding of an unchanged chat has to be stable.
        // That is what lets the controller answer a save from its cached payload instead of walking
        // the transcript again, and it is the only thing the change counter is allowed to rely on.
        val controller = AiChatController()
        controller.applySnapshot(snapshot)

        val first = AiChatPayload.encode(controller.snapshot())
        val second = AiChatPayload.encode(controller.snapshot())
        assertArrayEquals(first, second)

        // A real edit must show up: the draft is what a half-typed prompt leaves behind.
        controller.setDraft("a different thought")
        val edited = AiChatPayload.encode(controller.snapshot())
        assertTrue("an edited chat must encode differently", !first.contentEquals(edited))
        assertEquals("a different thought", AiChatPayload.decode(edited)?.draft)
        controller.dispose()
    }

    @Test
    fun anEmptyControllerRoundTripsToAnEmptyChat() {
        val controller = AiChatController()
        val payload = AiChatPayload.encode(controller.snapshot())

        val restored = AiChatPayload.decode(payload)
        assertEquals(AiChatSnapshot(), restored)
        assertNull(restored?.goal)
        assertEquals(emptyList<Any>(), restored?.messages)
        controller.dispose()
    }
}
