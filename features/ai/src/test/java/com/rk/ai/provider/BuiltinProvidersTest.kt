package com.rk.ai.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinProvidersTest {
    @Test
    fun `every provider is listed exactly once`() {
        val providers = BuiltinProviders.all()

        assertEquals(providers.size, providers.map { it.id }.distinct().size)
        assertEquals(providers.size, providers.map { it.displayName }.distinct().size)
    }

    @Test
    fun `every provider can be used out of the box`() {
        BuiltinProviders.all().forEach { provider ->
            assertTrue("${provider.id} has no name", provider.displayName.isNotBlank())
            assertTrue(
                "${provider.id} has no base url",
                provider.baseUrl.startsWith("https://") || provider.baseUrl.startsWith("http://"),
            )
            assertTrue("${provider.id} has no request path", provider.requestPath.isNotBlank())
            assertTrue("${provider.id} lists no model", provider.models.isNotEmpty())
            assertTrue(
                "${provider.id} has models of another provider",
                provider.models.all { it.providerId == provider.id },
            )
        }
    }

    @Test
    fun `the default provider is one of the built-ins`() {
        assertTrue(BuiltinProviders.all().any { it.id == BuiltinProviders.default().id })
        assertEquals(BuiltinProviders.DEEPSEEK_ID, BuiltinProviders.default().id)
    }
}
