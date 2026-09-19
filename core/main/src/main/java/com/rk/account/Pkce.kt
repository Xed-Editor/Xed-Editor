package com.rk.account

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal object Pkce {
    private const val VERIFIER_BYTES = 32

    private val random = SecureRandom()
    private val urlEncoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    fun createVerifier(): String {
        val bytes = ByteArray(VERIFIER_BYTES)
        random.nextBytes(bytes)
        return urlEncoder.encodeToString(bytes)
    }

    fun createChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return urlEncoder.encodeToString(digest)
    }
}
