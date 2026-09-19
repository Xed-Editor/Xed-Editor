package com.rk.ai.tools

import com.rk.exec.ShellUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellResultFormatTest {
    @Test
    fun includesExitCodeAndStdout() {
        val text = ShellTools.formatShellResult(ShellUtils.Result(0, "hello", "", false), 60)

        assertTrue(text.contains("exit=0"))
        assertTrue(text.contains("hello"))
        assertFalse("no stderr section when stderr is empty", text.contains("stderr:"))
    }

    @Test
    fun reportsTimeout() {
        val text = ShellTools.formatShellResult(ShellUtils.Result(-1, "", "", true), 30)

        assertTrue(text.contains("Timed out after 30s"))
        assertTrue(text.contains("exit=-1"))
    }

    @Test
    fun includesStderrWhenPresent() {
        val text = ShellTools.formatShellResult(ShellUtils.Result(1, "partial output", "boom", false), 60)

        assertTrue(text.contains("exit=1"))
        assertTrue(text.contains("partial output"))
        assertTrue(text.contains("stderr:"))
        assertTrue(text.contains("boom"))
    }

    @Test
    fun truncatesVeryLongOutput() {
        val huge = "x".repeat(100_000)

        val text = ShellTools.formatShellResult(ShellUtils.Result(0, huge, "", false), 60)

        assertTrue("output should be capped", text.length <= 32_000)
    }
}
