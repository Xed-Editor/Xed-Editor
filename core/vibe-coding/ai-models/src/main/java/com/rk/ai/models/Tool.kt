@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.google.gson.JsonElement
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonObject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
data class Tool(
    val name: String,
    val description: String,
    @Transient
    val parameters: () -> InputSchema? = { null },
    @Transient
    val systemPrompt: (modelId: Uuid, messages: List<UIMessage>) -> String = { _, _ -> "" },
    val needsApproval: Boolean = false,
    @Transient
    val execute: suspend (JsonElement) -> List<UIMessagePart>
)

@Serializable
sealed class InputSchema {
    @Serializable
    @SerialName("object")
    data class Obj(
        val properties: JsonObject,
        val required: List<String>? = null,
    ) : InputSchema()
}
