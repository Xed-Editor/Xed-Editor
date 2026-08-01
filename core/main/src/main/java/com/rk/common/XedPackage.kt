package com.rk.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.util.zip.ZipFile

enum class PackageType {
    @SerialName("extension") EXTENSION,
    @SerialName("theme") THEME,
    @SerialName("icon_pack") ICON_PACK,
}

@Serializable
class PackageManifest(
    val type: PackageType? = null,

    // Theme-specific
    val light: JsonObject? = null,
    val dark: JsonObject? = null,

    // Icon pack-specific
    val icons: JsonObject? = null,
)

object XedPackage {
    private val json = Json {
        ignoreUnknownKeys = true
        allowTrailingComma = true
    }

    fun extract(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val target = File(destDir, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    fun detectPackageType(dir: File): PackageType? {
        val manifestFile = File(dir, "manifest.json")
        val themeFile = File(dir, "theme.json")

        if (themeFile.exists()) {
            return PackageType.THEME
        }

        if (!manifestFile.exists()) {
            return null
        }

        val manifest = json.decodeFromString<PackageManifest>(manifestFile.readText())

        // New manifest format
        manifest.type?.let {
            return it
        }

        // Legacy formats
        if (manifest.icons != null) {
            return PackageType.ICON_PACK
        }

        if (manifest.light != null || manifest.dark != null) {
            return PackageType.THEME
        }

        val containsApk = dir.listFiles()?.any { it.extension == "apk" } ?: false
        if (containsApk) {
            return PackageType.EXTENSION
        }

        return null
    }
}
