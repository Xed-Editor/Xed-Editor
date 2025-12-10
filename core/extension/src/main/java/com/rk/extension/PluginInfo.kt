package com.rk.extension

import java.io.Serializable

typealias ExtensionId = String

/** @property id Unique identifier of the extension (package name) */
data class PluginInfo(
    val id: ExtensionId,
    val name: String,
    val mainClass: String,
    val version: String = "1.0.0",
    val description: String = "",
    val authors: List<String> = emptyList(),
    val minAppVersion: Int = -1, // -1 means supports all versions
    val targetAppVersion: Int = -1, // -1 means supports all versions
    val icon: String? = null,
    val screenshots: List<String> = emptyList(),
    val repository: String = "",
    val tags: List<String> = emptyList(),
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginInfo) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class CachedPlugin(
    val sha: String, // from GitHub
    val metadata: PluginInfo,
    val lastFetched: Long,
) : Serializable {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedPlugin) return false
        return sha == other.sha
    }

    override fun hashCode(): Int = sha.hashCode()
}
