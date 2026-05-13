package com.rk.icons.pack

import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.FileTypeManager
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
    fun getIconFileForFile(file: FileObject, isExpanded: Boolean = false): File? {
        val fileName = file.getName()
        val isDirectory = file.isDirectory()
        return getIconFileForName(fileName, isDirectory, isExpanded)
    }

    fun getIconFileForName(fileName: String, isDirectory: Boolean, isExpanded: Boolean = false): File? {
        val path =
            if (isDirectory) {
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
                val ext = fileName.substringAfterLast(".", "")

                info.icons.fileNames[fileName.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                    ?: info.icons.fileExtensions[ext.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: info.icons.languageNames[FileTypeManager.fromExtension(ext).name.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: installDir.resolve(info.icons.defaultFile)
            }

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }

    fun getIconFileForExt(fileExtension: String): File? {
        val path =
            // First use fileExtensions, then languageNames, then defaultFile
            info.icons.fileExtensions[fileExtension.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: info.icons.languageNames[FileTypeManager.fromExtension(fileExtension).name.lowercase()]
                    ?.let { installDir.resolve(it) }
                    ?.takeIf { it.exists() }
                ?: installDir.resolve(info.icons.defaultFile)

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }

    fun getIconFileForFileType(fileType: FileType): File? {
        val extension = fileType.extensions.firstOrNull()?.lowercase()
        val typeName = fileType.name.lowercase()

        val path =
            // First use fileExtensions, then languageNames, then defaultFile
            extension?.let { info.icons.fileExtensions[it] }?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: info.icons.languageNames[typeName]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: installDir.resolve(info.icons.defaultFile)

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }
}
