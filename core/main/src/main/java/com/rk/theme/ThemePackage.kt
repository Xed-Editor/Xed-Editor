package com.rk.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.common.PackageType
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.Package
import com.rk.extension.model.PackageAuthor
import com.rk.extension.model.Review
import com.rk.extension.model.UpdatablePackage
import io.github.z4kn4fein.semver.toVersionOrNull

interface ThemePackage : Package {
    override val type: PackageType
        get() = PackageType.THEME
}

data class StoreTheme(val entry: ThemeEntry) : ThemePackage {
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

    override val repository: String
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
        get() = StoreManager.getThemeIconUrl(id)

    override val readmeUrl: String
        get() = StoreManager.getThemeReadmeUrl(id)

    override val changelogUrl: String
        get() = StoreManager.getThemeChangelogUrl(id)

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

data class LocalTheme(
    val manifest: ThemeManifest,
    val installPath: String,
    override val createdAt: Long?,
    override val updatedAt: Long?,
    val initSize: Long?,
) : ThemePackage {
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

    override val repository: String
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
}

data class UpdatableTheme(val installed: LocalTheme, val store: StoreTheme) : ThemePackage, UpdatablePackage {
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

    override val repository: String
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
