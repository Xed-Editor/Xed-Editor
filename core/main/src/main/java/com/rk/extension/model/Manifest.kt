package com.rk.extension.model

import kotlinx.serialization.Serializable

typealias ExtensionId = String

@Serializable
data class ExtensionManifest(
    val id: ExtensionId,
    val name: String,
    val mainClass: String,
    val version: String = "1.0.0",
    val description: String? = null,
    val author: PackageAuthor = PackageAuthor.UNKNOWN,
    val minAppVersion: Int? = null, // null means no minimum restriction
    val supportedArchitectures: List<String>? = null,
    val repository: String,
    val license: String? = null,
    val localization: List<String>? = null, // e.g. listOf("en", "de", "ja")
    val dependencies: List<ExtensionId> = emptyList(),
    val recommendations: List<ExtensionId> = emptyList(),
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
