package com.rk.ai.core.cache

import kotlinx.serialization.KSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import java.util.Base64

interface KeyCodec<K : Any> {
    fun toFileName(key: K): String
    fun fromFileName(name: String): K?
}

class Base64JsonKeyCodec<K : Any>(
    private val keySerializer: KSerializer<K>,
    private val json: Json = Json { allowStructuredMapKeys = true }
) : KeyCodec<K> {
    override fun toFileName(key: K): String {
        val jsonStr = json.encodeToString(keySerializer, key)
        val bytes = jsonStr.toByteArray(StandardCharsets.UTF_8)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    override fun fromFileName(name: String): K? = try {
        val decoded = Base64.getUrlDecoder().decode(name)
        val jsonStr = String(decoded, StandardCharsets.UTF_8)
        json.decodeFromString(keySerializer, jsonStr)
    } catch (_: Exception) {
        null
    }
}

