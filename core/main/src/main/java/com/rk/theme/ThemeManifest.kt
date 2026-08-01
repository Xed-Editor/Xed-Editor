package com.rk.theme

import com.rk.extension.model.PackageAuthor
import kotlinx.serialization.Serializable

@Serializable
data class ThemeManifest(
    val id: String,
    val name: String,
    val author: PackageAuthor? = null,
    val version: String = "1.0.0",
    val description: String? = null,
    val minAppVersion: Int? = null,
    val inheritBase: Boolean = true,
    val light: ThemePalette? = null,
    val dark: ThemePalette? = null,
)
