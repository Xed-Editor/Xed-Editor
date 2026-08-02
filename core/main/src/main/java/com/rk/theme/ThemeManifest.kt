package com.rk.theme

import com.rk.extension.model.PackageAuthor
import kotlinx.serialization.Serializable

@Serializable
data class ThemeManifest(
    val id: String,
    val name: String,
    val author: PackageAuthor = PackageAuthor.UNKNOWN,
    val version: String = "1.0.0",
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val repository: String = "",
    val license: String? = null,
    val minAppVersion: Int? = null,
    val inheritBase: Boolean = true,
    val light: ThemePaletteNew? = null,
    val dark: ThemePaletteNew? = null,
)
