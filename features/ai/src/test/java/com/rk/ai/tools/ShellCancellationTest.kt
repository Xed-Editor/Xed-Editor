package com.rk.ai.tools

import com.rk.exec.ShellUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellCancellationTest {
    @Test
    fun runCapturesOutputAndExitCode() = runBlocking {
        val result = ShellUtils.run(command = arrayOf("sh", "-c", "echo hello; exit 3"))

        assertEquals(3, result.exitCode)
        assertTrue(result.output.contains("hello"))
    }

    @Test
    fun cancellingRunTerminatesTheProcess() = runBlocking {
        val job = launch(Dispatchers.IO) { ShellUtils.run(command = arrayOf("sh", "-c", "sleep 60")) }

        delay(300)

        val start = System.nanoTime()
        withTimeout(10_000) { job.cancelAndJoin() }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue("Stop should kill the command, but took ${elapsedMs}ms", elapsedMs < 5_000)
        assertTrue("job should be cancelled", job.isCancelled)
    }
}
