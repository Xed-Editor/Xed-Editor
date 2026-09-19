package com.rk.ai.api

import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import com.rk.ai.provider.AiModel
import com.rk.ai.provider.AiModelCatalog
import com.rk.ai.provider.AiModelRegistry
import com.rk.ai.provider.AiProvider
import com.rk.ai.provider.AiProviderConfig
import com.rk.ai.provider.AiProviderRegistry
import com.rk.ai.provider.BuiltinProviders
import com.rk.ai.tools.AiTool
import com.rk.ai.tools.AiToolKind
import com.rk.ai.tools.AiToolRegistry
import com.rk.ai.tools.ToolCallView
import com.rk.ai.tools.aiTool
import com.rk.ai.tools.displayArg
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the extension surface: built-ins installed once, extension registrations visible, overrides
 * restorable and the provider/model resolution used by the runtime and the settings UI.
 */
class AiExtensionRegistryTest {
    @After
    fun tearDown() {
        AiToolRegistry.resetForTests()
        AiProviderRegistry.resetForTests()
        AiModelRegistry.resetForTests()
    }

    @Test
    fun builtinsAreInstalledOnceAndVisible() {
        AiExtensions.installBuiltins()
        val first = AiToolRegistry.all().map { it.name }

        assertTrue(first.containsAll(listOf("read_file", "write_file", "run_ubuntu", "spawn_agent", "http_get")))
        assertTrue(AiExtensions.providers.value.any { it.id == BuiltinProviders.DEEPSEEK_ID })

        AiExtensions.installBuiltins()
        assertEquals("installing twice must not duplicate tools", first.size, AiToolRegistry.all().size)
    }

    @Test
    fun extensionToolIsRegisteredAndRemoved() {
        val tool = aiTool("unit_tool", "A test tool.", AiToolKind.Read) { executes { "ok" } }

        AiExtensions.registerTool(tool)
        assertSame(tool, AiToolRegistry.find("unit_tool"))
        assertTrue(AiExtensions.tools.value.any { it.name == "unit_tool" })

        AiExtensions.unregisterTool(tool)
        assertNull(AiToolRegistry.find("unit_tool"))
    }

    @Test
    fun overridingABuiltinRestoresItOnUnregister() {
        val builtin = AiToolRegistry.find("read_file")
        val replacement = aiTool("read_file", "Extension replacement.", AiToolKind.Read) { executes { "ok" } }

        AiExtensions.registerTool(replacement)
        assertSame(replacement, AiToolRegistry.find("read_file"))

        AiExtensions.unregisterTool(replacement)
        assertSame("dropping the override must bring the built-in back", builtin, AiToolRegistry.find("read_file"))
    }

    @Test
    fun toolNeedsExactlyOneBody() {
        assertThrows(IllegalArgumentException::class.java) {
            AiTool(name = "broken", description = "d", kind = AiToolKind.Read)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiTool(
                name = "broken",
                description = "d",
                kind = AiToolKind.Read,
                handler = { _, _ -> "h" },
                execute = { "e" },
            )
        }
    }

    @Test
    fun dslBuildsAToolWithPresenterAndSessionHandler() {
        val tool =
            aiTool("unit_session_tool", "Needs the session.", AiToolKind.Read) {
                stringParam("name", "Who to greet")
                intParam("times", "How often", required = false)
                mainAgentOnly()
                presents { args -> ToolCallView("Greet", args.displayArg("name"), emptyList()) }
                handles { _, _ -> "greeted" }
            }

        assertEquals(listOf("name", "times"), tool.parameters.map { it.name })
        assertEquals(listOf(true, false), tool.parameters.map { it.required })
        assertTrue(tool.mainAgentOnly)
        assertNotNull(tool.handler)
        assertNull(tool.execute)
        assertEquals("Greet", tool.presenter?.present(com.rk.ai.tools.parseToolArgs("""{"name":"Ada"}"""))?.action)
    }

    @Test
    fun providerIsRegisteredAndChosenByBaseUrl() {
        AiExtensions.registerProvider(fakeProvider("unit-provider", "https://unit.example", matches = true))

        assertEquals("unit-provider", AiProviderRegistry.find("unit-provider")?.id)
        // A base URL that belongs to the extension provider wins over the stale configured id.
        assertEquals("unit-provider", AiProviderRegistry.resolveActive("deepseek", "https://unit.example/v1")?.id)
        // A matching configured id still wins.
        assertEquals("deepseek", AiProviderRegistry.resolveActive("deepseek", "https://api.deepseek.com")?.id)

        AiExtensions.unregisterProvider("unit-provider")
        assertNull(AiProviderRegistry.find("unit-provider"))
    }

    @Test
    fun extensionModelIsMergedIntoItsProvider() {
        val model = AiModel(id = "deepseek-next", providerId = BuiltinProviders.DEEPSEEK_ID, displayName = "DeepSeek Next")

        AiExtensions.registerModel(model)

        val ids = AiModelCatalog.modelsFor(BuiltinProviders.DEEPSEEK_ID).map { it.id }
        assertTrue("expected the provider's own models, got $ids", ids.contains("deepseek-chat"))
        assertTrue(ids.contains("deepseek-next"))
        assertEquals(model, AiModelCatalog.find(BuiltinProviders.DEEPSEEK_ID, "deepseek-next"))

        AiExtensions.unregisterModel(model)
        assertNull(AiModelCatalog.find(BuiltinProviders.DEEPSEEK_ID, "deepseek-next"))
    }

    @Test
    fun duplicateModelIdsAreNotListedTwice() {
        AiExtensions.registerModel(AiModel(id = "deepseek-chat", providerId = BuiltinProviders.DEEPSEEK_ID))

        val matches = AiModelCatalog.modelsFor(BuiltinProviders.DEEPSEEK_ID).filter { it.id == "deepseek-chat" }
        assertEquals(1, matches.size)
    }

    private fun fakeProvider(id: String, baseUrl: String, matches: Boolean): AiProvider =
        object : AiProvider {
            override val id: String = id
            override val displayName: String = "Unit $id"
            override val defaultBaseUrl: String = baseUrl
            override val defaultChatCompletionsPath: String = "v1/chat/completions"
            override val llmProvider: LLMProvider = LLMProvider.OpenAI
            override val models: List<AiModel> = listOf(AiModel(id = "$id-model", providerId = id))

            override fun createExecutor(config: AiProviderConfig): PromptExecutor =
                throw UnsupportedOperationException("not executed in tests")

            override fun matchesBaseUrl(baseUrl: String): Boolean = matches && baseUrl.contains("unit.example")
        }
}
