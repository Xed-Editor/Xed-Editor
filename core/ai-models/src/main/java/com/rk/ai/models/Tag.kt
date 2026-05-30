@file:OptIn(ExperimentalUuidApi::class)
package com.rk.ai.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

@Serializable
data class Tag(
    val id: Uuid,
    val name: String,
)
