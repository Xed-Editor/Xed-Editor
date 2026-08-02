package com.rk.extension.model

import kotlinx.serialization.Serializable

@Serializable
data class PackageAuthor(val displayName: String, val github: String? = null) {
    override fun toString() = displayName

    companion object {
        val UNKNOWN = PackageAuthor("Unknown")
    }
}
