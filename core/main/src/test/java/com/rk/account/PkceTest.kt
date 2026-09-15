package com.rk.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceTest {
    private val rfcVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

    private val rfcChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

    @Test
    fun `challenge matches the RFC 7636 S256 test vector`() {
        assertEquals(rfcChallenge, Pkce.createChallenge(rfcVerifier))
    }

    @Test
    fun `verifier is a fresh 43 character base64url string`() {
        val first = Pkce.createVerifier()
        val second = Pkce.createVerifier()

        assertEquals(43, first.length)
        assertTrue("verifier must be base64url", first.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertNotEquals(first, second)
    }
}
