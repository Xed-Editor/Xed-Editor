package com.rk.extension

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.extension.manager.ExtensionEntry
import com.rk.extension.manager.StoreManager
import com.rk.extension.model.ExtensionId
import com.rk.extension.model.ExtensionManifest
import com.rk.extension.model.PackageAuthor
import com.rk.xededitor.BuildConfig
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.File
import java.util.Date

sealed interface Extension {
    val id: ExtensionId
    val name: String
    val version: String
    val author: PackageAuthor
    val description: String?
    val tags: List<String>
    val repository: String
    val license: String?
    val dependencies: List<ExtensionId>
    val recommendations: List<ExtensionId>
    val hasSettings: Boolean
    val iconUrl: String
    val readmeUrl: String
    val changelogUrl: String
    val minAppVersion: Int?
    val supportedArchitectures: List<String>?
    val downloads: Int?
    val rating: Float?
    val size: Long?
    val createdAt: Long?
    val updatedAt: Long?

    suspend fun getReviews(): List<Review>
}

data class Review(val rating: Int, val text: String, val author: String, val date: Date, val authorResponse: String?)

/** Extensions that are published in the store (online registry). Might or might not be installed locally. */
data class StoreExtension(private val entry: ExtensionEntry) : Extension {

    val manifest
        get() = entry.manifest

    override val id
        get() = manifest.id

    override val name
        get() = manifest.name

    override val version
        get() = manifest.version

    override val author
        get() = manifest.author

    override val description
        get() = manifest.description

    override val tags
        get() = manifest.tags

    override val repository
        get() = manifest.repository

    override val license
        get() = manifest.license

    override val dependencies
        get() = manifest.dependencies

    override val recommendations
        get() = manifest.recommendations

    override val hasSettings: Boolean
        get() = manifest.hasSettings

    override val iconUrl: String
        get() = StoreManager.getIconUrl(manifest.id)

    override val readmeUrl: String
        get() = StoreManager.getReadmeUrl(manifest.id)

    override val changelogUrl
        get() = StoreManager.getChangelogUrl(manifest.id)

    override val minAppVersion
        get() = manifest.minAppVersion

    override val supportedArchitectures
        get() = manifest.supportedArchitectures

    override val downloads
        get() = entry.downloads

    override val rating
        get() = null

    override val size
        get() = entry.size ?: entry.download.size

    override val createdAt
        get() = entry.createdAt

    override val updatedAt
        get() = entry.updatedAt

    override suspend fun getReviews(): List<Review> = emptyList()
}

/** Extensions that are installed locally (from disk). */
data class LocalExtension(
    val manifest: ExtensionManifest,
    val installPath: String,
    val enabled: Boolean = true,
    val initSize: Long?,
    override val createdAt: Long?,
    override val updatedAt: Long?,
) : Extension {
    override fun equals(other: Any?): Boolean {
        if (other !is LocalExtension) {
            return false
        }

        return other.id == id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override val id
        get() = manifest.id

    override val name
        get() = manifest.name

    override val version
        get() = manifest.version

    override val author
        get() = manifest.author

    override val description
        get() = manifest.description

    override val tags
        get() = manifest.tags

    override val repository
        get() = manifest.repository

    override val license
        get() = manifest.license

    override val dependencies
        get() = manifest.dependencies

    override val recommendations
        get() = manifest.recommendations

    override val hasSettings: Boolean
        get() = manifest.hasSettings

    override val iconUrl
        get() = "$installPath/icon.png"

    override val readmeUrl
        get() = "$installPath/README.md"

    override val changelogUrl
        get() = "$installPath/CHANGELOG.md"

    override val minAppVersion
        get() = manifest.minAppVersion

    override val supportedArchitectures
        get() = manifest.supportedArchitectures

    override val downloads
        get() = null

    override val rating
        get() = null

    override var size by mutableStateOf(initSize)

    override suspend fun getReviews(): List<Review> = emptyList()
}

data class UpdatableExtension(val installed: LocalExtension, val store: StoreExtension) : Extension {
    override val id
        get() = store.id

    override val name
        get() = store.name

    override val version
        get() = installed.version

    val newVersion: String
        get() = store.version

    override val author
        get() = store.author

    override val description
        get() = store.description

    override val tags
        get() = store.tags

    override val repository
        get() = store.repository

    override val license
        get() = store.license

    override val dependencies: List<ExtensionId>
        get() = store.dependencies

    override val recommendations
        get() = store.recommendations

    override val hasSettings: Boolean
        get() = installed.hasSettings

    override val iconUrl
        get() = if (hasUpdate()) store.iconUrl else installed.iconUrl

    override val readmeUrl
        get() = if (hasUpdate()) store.readmeUrl else installed.readmeUrl

    override val changelogUrl
        get() = if (hasUpdate()) store.changelogUrl else installed.changelogUrl

    override val minAppVersion
        get() = store.minAppVersion

    override val supportedArchitectures
        get() = store.supportedArchitectures

    override val downloads
        get() = store.downloads

    override val rating
        get() = store.rating

    override val size
        get() = installed.size

    override val createdAt
        get() = store.createdAt

    override val updatedAt
        get() = store.updatedAt

    override suspend fun getReviews() = store.getReviews()

    fun hasUpdate(): Boolean {
        val installedVersion = installed.version.toVersionOrNull() ?: return false
        val storeVersion = store.version.toVersionOrNull() ?: return false
        return installedVersion < storeVersion
    }
}

val LocalExtension.apkFile: File
    get() = run {
        val dir = File(installPath)

        if (!dir.isDirectory) error("Extension [$name, $id] directory not found")

        val apks = dir.listFiles { it.extension == "apk" } ?: emptyArray()
        if (apks.isEmpty()) error("APK not found")

        val apk =
            if (apks.size == 1) {
                apks.first()
            } else {
                val isDebug = BuildConfig.DEBUG
                if (isDebug) {
                    apks.find { it.name.contains("debug", ignoreCase = true) }
                        ?: apks.find { !it.name.contains("release", ignoreCase = true) }
                        ?: apks.first()
                } else {
                    apks.find { it.name.contains("release", ignoreCase = true) }
                        ?: apks.find { !it.name.contains("debug", ignoreCase = true) }
                        ?: apks.first()
                }
            }
        apk.also { it.setReadOnly() }
    }
