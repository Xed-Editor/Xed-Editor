package com.rk.extension.github

import android.util.Base64
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

fun String.decodeFromBase64(): ByteArray? = Base64.decode(this, Base64.DEFAULT)

@Serializable
data class FileContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val url: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("git_url") val gitUrl: String,
    @SerialName("download_url") val downloadUrl: String? = null,
    val type: String, // "file" or "dir"
    val content: String? = null,
    val encoding: String? = null,
)
