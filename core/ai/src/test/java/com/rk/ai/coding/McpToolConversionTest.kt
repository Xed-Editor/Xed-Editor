@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.rk.ai.coding

import com.rk.ai.coding.fakes.FakeIdeService
import com.rk.ai.coding.tools.ExternalMcpTool
import com.rk.ai.coding.tools.McpToolSource
import com.rk.ai.coding.tools.NativeToolContext
import com.rk.ai.coding.tools.ToolRegistry
import com.rk.ai.models.InputSchema
import com.rk.ai.models.UIMessagePart
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.uuid.Uuid

class McpToolConversionTest {
    @Test
    fun convertsExternalMcpToolsToGenerationTools() = runBlocking {
        val serverId = Uuid.random()
        val source = FakeMcpToolSource(serverId)
        val registry = ToolRegistry(mcpToolSource = source)
        val tools = registry.asGenerationTools(NativeToolContext(FakeIdeService(File("/tmp/native-mcp"))))
        val tool = tools.first { it.name == "external_lookup" }

        val result = tool.execute(buildJsonObject { put("query", "abc") })

        assertEquals("external_lookup", tool.name)
        assertTrue(tool.description.contains("External"))
        assertEquals(listOf("query"), (tool.parameters() as InputSchema.Obj).required)
        assertEquals("called external_lookup", (result.single() as UIMessagePart.Text).text)
        assertEquals(serverId, source.lastServerId)
    }

    private class FakeMcpToolSource(private val serverId: Uuid) : McpToolSource {
        var lastServerId: Uuid? = null

        override fun availableTools(): List<ExternalMcpTool> = listOf(
            ExternalMcpTool(
                serverId = serverId,
                name = "external_lookup",
                description = "External lookup tool",
                inputSchema = InputSchema.Obj(
                    properties = buildJsonObject {
                        put("query", JsonObject(mapOf("type" to kotlinx.serialization.json.JsonPrimitive("string"))))
                    },
                    required = listOf("query"),
                ),
                needsApproval = false,
            )
        )

        override suspend fun callTool(serverId: Uuid, toolName: String, args: JsonObject): List<UIMessagePart> {
            lastServerId = serverId
            return listOf(UIMessagePart.Text("called $toolName"))
        }
    }
}
