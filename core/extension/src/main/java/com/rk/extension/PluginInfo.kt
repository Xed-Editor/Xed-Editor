package com.rk.extension

import java.io.Serializable

typealias ExtensionId = String

/**
 * @property id Unique identifier of the extension (package name)
 */
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
    val tags: List<String> = emptyList()
) : Serializable
