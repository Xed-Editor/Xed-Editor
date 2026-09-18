package com.rk.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiWorkspacePathTest {
    @Test
    fun collapsesEquivalentSpellings() {
        val expected = "src/main/App.kt"

        assertEquals(expected, AiWorkspace.normalizePath("src/main/App.kt"))
        assertEquals(expected, AiWorkspace.normalizePath("./src/main/App.kt"))
        assertEquals(expected, AiWorkspace.normalizePath("/src/main/App.kt"))
        assertEquals(expected, AiWorkspace.normalizePath("  src/main/App.kt  "))
        assertEquals(expected, AiWorkspace.normalizePath("src//main/App.kt"))
        assertEquals(expected, AiWorkspace.normalizePath("src\\main\\App.kt"))
    }

    @Test
    fun workspaceRootIsTheEmptyKey() {
        assertEquals("", AiWorkspace.normalizePath("."))
        assertEquals("", AiWorkspace.normalizePath(""))
    }

    @Test
    fun rejectsPathsThatEscapeTheWorkspace() {
        assertThrows(IllegalArgumentException::class.java) {
            AiWorkspace.normalizePath("../secrets.txt")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiWorkspace.normalizePath("src/../../secrets.txt")
        }
    }
}
