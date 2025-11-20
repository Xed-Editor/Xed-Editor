package com.rk.extension.github

import android.util.Base64
import com.google.gson.annotations.SerializedName
import java.io.Serializable

fun String.decodeFromBase64(): ByteArray? = Base64.decode(this, Base64.DEFAULT)

data class FileContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val url: String,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("git_url") val gitUrl: String,
    @SerializedName("download_url") val downloadUrl: String?,
    val type: String, // "file" or "dir"
    val content: String?,
    val encoding: String?,
) : Serializable
