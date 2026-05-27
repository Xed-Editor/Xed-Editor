package com.rk.extension

import kotlinx.serialization.Serializable

typealias ExtensionId = String

/** @property id Unique identifier of the extension (package name) */
@Serializable
data class ExtensionManifest(
    val id: ExtensionId,
    val name: String,
    val mainClass: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val author: ExtensionAuthor,
    val minAppVersion: Int? = null, // null means no minimum restriction
    val maxAppVersion: Int? = null, // null means no maximum restriction
    val repository: String,
    val license: String? = null,
    val tags: List<String> = emptyList(),
    val hasSettings: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExtensionManifest) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
data class CachedExtension(
    val sha: String, // from GitHub
    val metadata: ExtensionManifest,
    val lastFetched: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedExtension) return false
        return sha == other.sha
    }

    override fun hashCode(): Int = sha.hashCode()
}
