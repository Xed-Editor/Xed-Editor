package com.rk.ai.models

import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
data class Tag(
    val id: Uuid,
    val name: String,
)
