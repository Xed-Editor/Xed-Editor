package com.rk.ai.core.cache

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure

@Serializable
data class CacheEntry<V>(val value: V, val expiresAt: Long? = null) {
    fun isExpired(nowMillis: Long): Boolean = expiresAt?.let { nowMillis >= it } ?: false
}

@OptIn(ExperimentalSerializationApi::class)
internal fun <V> cacheEntrySerializer(valueSerializer: KSerializer<V>): KSerializer<CacheEntry<V>> =
    object : KSerializer<CacheEntry<V>> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("CacheEntry") {
            element("value", valueSerializer.descriptor)
            element("expiresAt", Long.serializer().descriptor, isOptional = true)
        }

        override fun serialize(encoder: Encoder, value: CacheEntry<V>) {
            encoder.encodeStructure(descriptor) {
                encodeSerializableElement(descriptor, 0, valueSerializer, value.value)
                value.expiresAt?.let {
                    encodeNullableSerializableElement(descriptor, 1, Long.serializer(), it)
                }
            }
        }

        override fun deserialize(decoder: Decoder): CacheEntry<V> {
            var v: V? = null
            var exp: Long? = null
            decoder.decodeStructure(descriptor) {
                loop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        -1 -> break@loop
                        0 -> v = decodeSerializableElement(descriptor, 0, valueSerializer)
                        1 -> exp = decodeNullableSerializableElement(descriptor, 1, Long.serializer())
                        else -> {
                            // ignore unknown
                        }
                    }
                }
            }
            val nonNull = v ?: throw IllegalStateException("CacheEntry.value is missing")
            return CacheEntry(nonNull, exp)
        }
    }

