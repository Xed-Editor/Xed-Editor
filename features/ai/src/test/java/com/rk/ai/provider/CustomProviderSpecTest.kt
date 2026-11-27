package com.rk.ai.provider

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomProviderSpecTest {
    @Test
    fun `accepts http and https base urls`() {
        assertTrue(CustomProviderSpec.isHttpUrl("https://api.example.com"))
        assertTrue(CustomProviderSpec.isHttpUrl("http://127.0.0.1:11434"))
        assertTrue(CustomProviderSpec.isHttpUrl("https://api.example.com/v1"))
        assertTrue(CustomProviderSpec.isHttpUrl("  https://api.example.com  "))
    }

    @Test
    fun `rejects base urls without a host or scheme`() {
        assertFalse(CustomProviderSpec.isHttpUrl(""))
        assertFalse(CustomProviderSpec.isHttpUrl("api.example.com"))
        assertFalse(CustomProviderSpec.isHttpUrl("ftp://api.example.com"))
        assertFalse(CustomProviderSpec.isHttpUrl("https://"))
        assertFalse(CustomProviderSpec.isHttpUrl("https:///v1/chat"))
    }

    @Test
    fun `a record needs a name and a url before it can be registered`() {
        assertEquals(CustomProviderProblem.MISSING_NAME, CustomProviderSpec(id = "custom-1").problem)

        val unnamed = CustomProviderSpec(id = "custom-1", baseUrl = "http://host")
        assertEquals(CustomProviderProblem.MISSING_NAME, unnamed.problem)

        val badUrl = CustomProviderSpec(id = "custom-1", name = "Server", baseUrl = "host")
        assertEquals(CustomProviderProblem.INVALID_BASE_URL, badUrl.problem)
        assertFalse(badUrl.isUsable)

        val usable = CustomProviderSpec(id = "custom-1", name = "Server", baseUrl = "http://host")
        assertNull(usable.problem)
        assertTrue(usable.isUsable)
    }

    @Test
    fun `model ids are parsed from commas and newlines`() {
        assertEquals(
            listOf("a", "b", "c"),
            CustomProviderSpec.parseModelIds("a, b\n c ,, a"),
        )
        assertEquals(emptyList<String>(), CustomProviderSpec.parseModelIds("  "))
        assertEquals("a, b", CustomProviderSpec(models = listOf("a", " b ", "a")).modelIdsText())
    }

    @Test
    fun `round trips through the stored json form`() {
        val serializer = ListSerializer(CustomProviderSpec.serializer())
        val specs =
            listOf(
                CustomProviderSpec(
                    id = "custom-1",
                    name = "Server",
                    baseUrl = "http://host",
                    requestPath = "v1/chat/completions",
                    requiresApiKey = false,
                    models = listOf("a", "b"),
                )
            )

        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        assertEquals(specs, json.decodeFromString(serializer, json.encodeToString(serializer, specs)))
    }
}
