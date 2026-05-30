package com.rk.ai.coding

import com.google.gson.JsonObject
import com.rk.ai.coding.fakes.FakeIdeService
import com.rk.ai.coding.tools.GitStatusTool
import com.rk.ai.coding.tools.NativeToolContext
import com.rk.ai.coding.tools.ReadFileTool
import com.rk.ai.coding.tools.SearchTool
import com.rk.ai.coding.tools.ToolPermissionManager
import com.rk.ai.coding.tools.ToolPermissionLevel
import com.rk.ai.coding.tools.WriteFileTool
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeToolsTest {
    @Test
    fun readFileSupportsLineRanges() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-tools"))
        fake.putFile("src/A.kt", "line1\nline2\nline3")
        val result = ReadFileTool().execute(
            JsonObject().apply {
                addProperty("path", "src/A.kt")
                addProperty("startLine", 2)
                addProperty("endLine", 2)
            },
            NativeToolContext(fake),
        )

        assertTrue(result.success)
        assertEquals("line2", result.text)
    }

    @Test
    fun searchToolUsesProjectOps() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-search"))
        fake.putFile("src/A.kt", "class Target")
        val result = SearchTool().execute(
            JsonObject().apply { addProperty("query", "Target") },
            NativeToolContext(fake),
        )

        assertTrue(result.success)
        assertTrue(result.text.contains("src/A.kt"))
    }

    @Test
    fun gitStatusIsReadOnly() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-git"))
        val result = GitStatusTool().execute(JsonObject(), NativeToolContext(fake))

        assertTrue(result.success)
        assertTrue(result.text.contains("main"))
    }

    @Test
    fun writeFileOpensPreviewInsteadOfApplyingImmediately() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-write"))
        val path = fake.putFile("src/A.kt", "old")
        val result = WriteFileTool().execute(
            JsonObject().apply {
                addProperty("filePath", path)
                addProperty("content", "new")
            },
            NativeToolContext(fake),
        )

        assertTrue(result.success)
        assertEquals("old", fake.files[path])
        assertNotNull(fake.lastPatch)
        assertEquals("new", fake.lastPatch?.newContent)
    }

    @Test
    fun permissionPreviewBuildsDiffForFileWrites() = runBlocking {
        val fake = FakeIdeService(File("/tmp/native-preview"))
        val path = fake.putFile("src/A.kt", "old")
        val request = ToolPermissionManager().buildPreview(
            sessionId = "s1",
            toolCallId = "t1",
            toolName = "writeFile",
            input = JsonObject().apply {
                addProperty("filePath", path)
                addProperty("content", "new")
            },
            rawInput = "{}",
            context = NativeToolContext(fake),
        )

        assertEquals(ToolPermissionLevel.Ask, request.permission)
        assertTrue(request.preview?.unifiedDiff?.contains("-old") == true)
        assertTrue(request.preview?.unifiedDiff?.contains("+new") == true)
    }
}
