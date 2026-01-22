package com.rk.icons.pack

import com.rk.file.FileObject
import com.rk.file.FileType
import java.io.File
import kotlinx.serialization.Serializable

typealias IconPackId = String

typealias IconPackPath = String

@Serializable
data class IconPackInfo(val id: IconPackId, val name: String, val applyTint: Boolean = false, val icons: IconPackList)

@Serializable
data class IconPackList(
    val defaultFile: IconPackPath,
    val defaultFolder: IconPackPath,
    val defaultFolderExpanded: IconPackPath,
    val folderNames: Map<String, IconPackPath> = emptyMap(),
    val folderNamesExpanded: Map<String, IconPackPath> = emptyMap(),
    val fileNames: Map<String, IconPackPath> = emptyMap(),
    val fileExtensions: Map<String, IconPackPath> = emptyMap(),
    val languageNames: Map<String, IconPackPath> = emptyMap(),
)

data class IconPack(val info: IconPackInfo, val installDir: File) {
    fun getIconFileFor(file: FileObject, isExpanded: Boolean): File? {
        val fileName = file.getName()
        val path =
            if (file.isDirectory()) {
                if (isExpanded) {
                    // First use folderNamesExpanded, then defaultFolderExpanded
                    info.icons.folderNamesExpanded[fileName.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() } ?: installDir.resolve(info.icons.defaultFolderExpanded)
                } else {
                    // First use folderNames, then defaultFolder
                    info.icons.folderNames[fileName.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                        ?: installDir.resolve(info.icons.defaultFolder)
                }
            } else {
                // First use fileNames, then fileExtensions, then languageNames, then defaultFile
                val ext = fileName.substringAfterLast(".")

                info.icons.fileNames[fileName.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                    ?: info.icons.fileExtensions[ext.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: info.icons.languageNames[FileType.fromExtension(ext).name.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: installDir.resolve(info.icons.defaultFile)
            }

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }

    fun getIconFileFor(fileExtension: String): File? {
        val path =
            // First use fileExtensions, then languageNames, then defaultFile
            info.icons.fileExtensions[fileExtension.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: info.icons.languageNames[FileType.fromExtension(fileExtension).name.lowercase()]
                    ?.let { installDir.resolve(it) }
                    ?.takeIf { it.exists() }
                ?: installDir.resolve(info.icons.defaultFile)

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }
}
