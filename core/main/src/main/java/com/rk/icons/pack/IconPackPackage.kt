package com.rk.icons.pack

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.common.PackageType
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.Package
import com.rk.extension.model.PackageAuthor
import com.rk.extension.model.Review
import com.rk.extension.model.UpdatablePackage
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.file.FileTypeManager
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.File

interface IconPackPackage : Package {
    override val type: PackageType
        get() = PackageType.ICON_PACK
}

data class StoreIconPack(val entry: IconPackEntry) : IconPackPackage {
    override val id: String
        get() = entry.id

    override val name: String
        get() = entry.manifest.name

    override val version: String
        get() = entry.manifest.version

    override val author: PackageAuthor
        get() = entry.manifest.author

    override val description: String?
        get() = entry.manifest.description

    override val tags: List<String>
        get() = entry.manifest.tags

    override val repository: String?
        get() = entry.manifest.repository

    override val license: String?
        get() = entry.manifest.license

    override val dependencies: List<String>
        get() = emptyList()

    override val recommendations: List<String>
        get() = emptyList()

    override val hasSettings: Boolean
        get() = false

    override val iconUrl: String
        get() = StoreManager.getIconPackIconUrl(id)

    override val readmeUrl: String
        get() = StoreManager.getIconPackReadmeUrl(id)

    override val changelogUrl: String
        get() = StoreManager.getIconPackChangelogUrl(id)

    override val minAppVersion: Int?
        get() = entry.manifest.minAppVersion

    override val supportedArchitectures: List<String>?
        get() = null

    override val downloads: Int?
        get() = null

    override val rating: Float?
        get() = null

    override val size: Long?
        get() = entry.size

    override val createdAt: Long
        get() = entry.createdAt

    override val updatedAt: Long
        get() = entry.updatedAt

    override suspend fun getReviews(): List<Review> = emptyList()
}

data class LocalIconPack(
    val manifest: IconPackManifest,
    val installPath: String,
    override val createdAt: Long?,
    override val updatedAt: Long?,
    val initSize: Long?,
) : IconPackPackage {
    override val id: String
        get() = manifest.id

    override val name: String
        get() = manifest.name

    override val version: String
        get() = manifest.version

    override val author: PackageAuthor
        get() = manifest.author

    override val description: String?
        get() = manifest.description

    override val tags: List<String>
        get() = manifest.tags

    override val repository: String?
        get() = manifest.repository

    override val license: String?
        get() = manifest.license

    override val dependencies: List<String>
        get() = emptyList()

    override val recommendations: List<String>
        get() = emptyList()

    override val hasSettings: Boolean
        get() = false

    override val iconUrl: String
        get() = "$installPath/icon.png"

    override val readmeUrl: String
        get() = "$installPath/README.md"

    override val changelogUrl: String
        get() = "$installPath/CHANGELOG.md"

    override val minAppVersion: Int?
        get() = manifest.minAppVersion

    override val supportedArchitectures: List<String>?
        get() = null

    override val downloads: Int?
        get() = null

    override val rating: Float?
        get() = null

    override var size by mutableStateOf(initSize)

    override suspend fun getReviews(): List<Review> = emptyList()

    private val installDir: File
        get() = File(installPath)

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
                    manifest.icons.folderNamesExpanded[fileName.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() } ?: installDir.resolve(manifest.icons.defaultFolderExpanded)
                } else {
                    // First use folderNames, then defaultFolder
                    manifest.icons.folderNames[fileName.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() } ?: installDir.resolve(manifest.icons.defaultFolder)
                }
            } else {
                // First use fileNames, then fileExtensions, then languageNames, then defaultFile
                val ext = fileName.substringAfterLast(".", "")

                manifest.icons.fileNames[fileName.lowercase()]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                    ?: manifest.icons.fileExtensions[ext.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: manifest.icons.languageNames[FileTypeManager.fromExtension(ext).name.lowercase()]
                        ?.let { installDir.resolve(it) }
                        ?.takeIf { it.exists() }
                    ?: installDir.resolve(manifest.icons.defaultFile)
            }

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }

    fun getIconFileForExt(fileExtension: String): File? {
        val path =
            // First use fileExtensions, then languageNames, then defaultFile
            manifest.icons.fileExtensions[fileExtension.lowercase()]
                ?.let { installDir.resolve(it) }
                ?.takeIf { it.exists() }
                ?: manifest.icons.languageNames[FileTypeManager.fromExtension(fileExtension).name.lowercase()]
                    ?.let { installDir.resolve(it) }
                    ?.takeIf { it.exists() }
                ?: installDir.resolve(manifest.icons.defaultFile)

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }

    fun getIconFileForFileType(fileType: FileType): File? {
        val extension = fileType.extensions.firstOrNull()?.lowercase()
        val typeName = fileType.name.lowercase()

        val path =
            // First use fileExtensions, then languageNames, then defaultFile
            extension?.let { manifest.icons.fileExtensions[it] }?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: manifest.icons.languageNames[typeName]?.let { installDir.resolve(it) }?.takeIf { it.exists() }
                ?: installDir.resolve(manifest.icons.defaultFile)

        // If no icon was working (even the fallback ones)
        if (!path.exists()) return null

        return path
    }
}

data class UpdatableIconPack(val installed: LocalIconPack, val store: StoreIconPack) :
    IconPackPackage, UpdatablePackage {
    override val id: String
        get() = store.id

    override val name: String
        get() = store.name

    override val version: String
        get() = installed.version

    override val newVersion: String
        get() = store.version

    override val author: PackageAuthor
        get() = store.author

    override val description: String?
        get() = store.description

    override val tags: List<String>
        get() = store.tags

    override val repository: String?
        get() = store.repository

    override val license: String?
        get() = store.license

    override val dependencies: List<String>
        get() = store.dependencies

    override val recommendations: List<String>
        get() = store.recommendations

    override val hasSettings: Boolean
        get() = installed.hasSettings

    override val iconUrl: String
        get() = if (hasUpdate()) store.iconUrl else installed.iconUrl

    override val readmeUrl: String
        get() = if (hasUpdate()) store.readmeUrl else installed.readmeUrl

    override val changelogUrl: String
        get() = if (hasUpdate()) store.changelogUrl else installed.changelogUrl

    override val minAppVersion: Int?
        get() = store.minAppVersion

    override val supportedArchitectures: List<String>?
        get() = store.supportedArchitectures

    override val downloads: Int?
        get() = store.downloads

    override val rating: Float?
        get() = store.rating

    override val size: Long?
        get() = installed.size

    override val createdAt: Long
        get() = store.createdAt

    override val updatedAt: Long
        get() = store.updatedAt

    override suspend fun getReviews() = store.getReviews()

    override fun hasUpdate(): Boolean {
        val installedVersion = installed.version.toVersionOrNull() ?: return false
        val storeVersion = store.version.toVersionOrNull() ?: return false
        return installedVersion < storeVersion
    }
}
