package com.rk.ai.chat

import com.rk.ai.model.AiToolCall
import com.rk.ai.model.AiTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolHistoryTest {
    private val calls =
        listOf(
            AiToolCall(id = "call-1", name = "read_file", args = "{}"),
            AiToolCall(id = "call-2", name = "run_android_shell", args = "{}"),
            AiToolCall(id = "call-3", name = "write_file", args = "{}"),
        )

    @Test
    fun deniedCallAndTheRestGetToolResults() {
        val history = mutableListOf<AiTurn>()

        answerRemainingToolCalls(history, calls, fromIndex = 1, cause = RuntimeException("boom"))

        // The exchange must be replayable: an assistant turn's tool_calls each need a matching result.
        assertEquals(2, history.size)
        assertEquals(listOf("call-2", "call-3"), history.map { (it as AiTurn.ToolOutput).id })
        assertTrue(history.all { (it as AiTurn.ToolOutput).isError })
    }

    @Test
    fun alreadyRecordedCallsAreNotRepeated() {
        val history = mutableListOf<AiTurn>(AiTurn.ToolOutput("call-1", "read_file", "ok", false))

        answerRemainingToolCalls(history, calls, fromIndex = 1, cause = RuntimeException("boom"))

        assertEquals(listOf("call-1", "call-2", "call-3"), history.map { (it as AiTurn.ToolOutput).id })
    }

    @Test
    fun stoppedRunIsReportedAsSuch() {
        val history = mutableListOf<AiTurn>()

        answerRemainingToolCalls(
            history,
            calls,
            fromIndex = 0,
            cause = kotlinx.coroutines.CancellationException("stopped"),
        )

        assertEquals(3, history.size)
        assertTrue((history.first() as AiTurn.ToolOutput).output.contains("stopped the run"))
    }
}
