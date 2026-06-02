@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.providers

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.rk.ai.models.CustomBody
import com.rk.ai.models.CustomHeader
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable
data class Model(
    val modelId: String = "",
    val displayName: String = "",
    val id: Uuid = Uuid.random(),
    val type: ModelType = ModelType.CHAT,
    val customHeaders: List<CustomHeader> = emptyList(),
    val customBodies: List<CustomBody> = emptyList(),
    val inputModalities: List<Modality> = listOf(Modality.TEXT),
    val outputModalities: List<Modality> = listOf(Modality.TEXT),
    val abilities: List<ModelAbility> = emptyList(),
    val tools: Set<BuiltInTools> = emptySet(),
    val providerOverwrite: ProviderSetting? = null,
    val contextWindow: Int = 128_000,
    val maxOutputTokens: Int = 8_192,
)

@Serializable
enum class ModelType {
    CHAT,
    IMAGE,
    EMBEDDING,
}

@Serializable
enum class Modality {
    TEXT,
    IMAGE,
}

@Serializable
enum class ModelAbility {
    TOOL,
    REASONING,
}

// 模型(提供商)提供的内置工具选项
@Serializable
sealed class BuiltInTools {
    // https://ai.google.dev/gemini-api/docs/google-search?hl=zh-cn
    @Serializable
    @SerialName("search")
    data object Search : BuiltInTools()

    // https://ai.google.dev/gemini-api/docs/url-context?hl=zh-cn
    @Serializable
    @SerialName("url_context")
    data object UrlContext : BuiltInTools()

    @Serializable
    @SerialName("image_generation")
    data object ImageGeneration : BuiltInTools()
}



