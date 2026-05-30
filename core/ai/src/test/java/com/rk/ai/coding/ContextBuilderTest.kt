package com.rk.ai.coding

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.ai.coding.context.ContextBuildOptions
import com.rk.ai.coding.context.ContextBuilder
import com.rk.ai.coding.fakes.FakeIdeService
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextBuilderTest {
    @Test
    fun includesRelevantWorkspaceContextWithoutWholeProject() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-agent"))
        val activePath = fake.putFile("src/Main.kt", "class Main {\n    fun run() = Unit\n}")
        fake.putFile("src/Other.kt", "class Other")
        fake.selection = "fun run() = Unit"
        fake.gitStatus = JsonObject().apply {
            addProperty("branch", "main")
            addProperty("totalChanges", 1)
            add("changes", JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("file", "src/Main.kt")
                    addProperty("type", "working_tree_modified")
                })
            })
        }
        fake.terminalOutput = "BUILD FAILED"
        fake.diagnostics[activePath] = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("message", "Unresolved reference")
                addProperty("severity", "ERROR")
                add("range", JsonObject().apply {
                    add("start", JsonObject().apply {
                        addProperty("line", 2)
                        addProperty("character", 5)
                    })
                })
            })
        }

        val context = ContextBuilder(fake).build(
            userRequest = "fix build error and check git status",
            options = ContextBuildOptions(maxOpenTabs = 1),
        )

        assertEquals(fake.getPrimaryWorkspacePath(), context.workspaceRoot)
        assertEquals(activePath, context.currentFile?.path)
        assertEquals("fun run() = Unit", context.currentSelection)
        assertEquals(1, context.openTabs.size)
        assertNotNull(context.gitStatus)
        assertEquals(1, context.diagnostics.size)
        assertEquals("BUILD FAILED", context.terminalOutput)
        assertTrue(context.toPrompt().contains("Current file excerpt"))
    }
}
