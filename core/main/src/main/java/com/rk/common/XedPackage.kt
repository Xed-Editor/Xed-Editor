package com.rk.common

import com.rk.extension.model.PackageAuthor
import com.rk.file.unzipTo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File

enum class PackageType(val value: String) {
    @SerialName("extension") EXTENSION("extension"),
    @SerialName("theme") THEME("theme"),
    @SerialName("icon_pack") ICON_PACK("iconpack"),
}

@Serializable
data class PackageManifest(
    val id: String = "",
    val name: String = "",
    val version: String = "1.0.0",
    val author: PackageAuthor = PackageAuthor.UNKNOWN,
    val description: String? = null,
    val type: PackageType? = null,

    // Icon pack-specific
    val icons: JsonObject? = null,
)

object XedPackage {
    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    fun extract(zipFile: File, destDir: File) {
        zipFile.unzipTo(destDir)
    }

    fun loadManifest(dir: File): PackageManifest? {
        val manifestFile = File(dir, "manifest.json")
        val type = detectPackageType(dir) ?: return null

        if (manifestFile.exists()) {
            return runCatching {
                json.decodeFromString<PackageManifest>(manifestFile.readText()).copy(type = type)
            }
                .getOrNull()
        }

        return null
    }

    fun detectPackageType(dir: File): PackageType? {
        val manifestFile = File(dir, "manifest.json")

        if (manifestFile.exists()) {
            val manifest = json.decodeFromString<PackageManifest>(manifestFile.readText())

            // Explicit type takes precedence over guessing
            manifest.type?.let {
                return it
            }

            // Legacy formats
            if (manifest.icons != null) {
                return PackageType.ICON_PACK
            }

            val containsApk = dir.listFiles()?.any { it.extension == "apk" } ?: false
            if (containsApk) {
                return PackageType.EXTENSION
            }
        }

        // Legacy theme-only packages ship theme.json without a manifest.json
        val themeFile = File(dir, "theme.json")
        if (themeFile.exists()) {
            return PackageType.THEME
        }

        return null
    }
}
