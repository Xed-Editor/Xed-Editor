package com.rk.extension

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.mutableStateMapOf
import dalvik.system.PathClassLoader
import java.io.File
import java.util.Date
import kotlinx.serialization.Serializable

val loadedExtensions = mutableStateMapOf<LocalExtension, ExtensionAPI?>()

sealed interface Extension {
    val id: ExtensionId
    val name: String
    val version: String
    val authors: List<ExtensionAuthor>
    val description: String?
    val tags: List<String>
    val repository: String
    val license: String?

    suspend fun iconUrl(): String?

    suspend fun readmeUrl(): String?

    suspend fun getRating(): Float?

    suspend fun getReviews(): List<Review>

    suspend fun getDownloadCount(): Long?
}

data class Review(val rating: Int, val text: String, val author: String, val date: Date, val authorResponse: String?)

@Serializable
data class ExtensionAuthor(val displayName: String, val github: String? = null) {
    override fun toString() = displayName
}

/** Extensions that are published in the store (online registry). Might or might not be installed locally. */
data class StoreExtension(val manifest: ExtensionManifest, val verified: Boolean = false) : Extension {
    override val id
        get() = manifest.id

    override val name
        get() = manifest.name

    override val version
        get() = manifest.version

    override val authors
        get() = manifest.authors

    override val description
        get() = manifest.description

    override val tags
        get() = manifest.tags

    override val repository
        get() = manifest.repository

    override val license
        get() = manifest.license

    override suspend fun getRating() = null

    override suspend fun iconUrl(): String? = ExtensionRegistry.getIconUrl(manifest)

    override suspend fun readmeUrl(): String? = ExtensionRegistry.getReadmeUrl(manifest)

    override suspend fun getReviews(): List<Review> = emptyList()

    override suspend fun getDownloadCount() = null
}

/** Extensions that are installed locally (from disk). */
data class LocalExtension(
    val manifest: ExtensionManifest,

    // Path where extension is installed
    val installPath: String,

    // Whether it’s enabled / disabled by the user
    val enabled: Boolean = true,
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

    override val authors
        get() = manifest.authors

    override val description
        get() = manifest.description

    override val tags
        get() = manifest.tags

    override val repository
        get() = manifest.repository

    override val license
        get() = manifest.license

    override suspend fun iconUrl(): String? {
        return ExtensionRegistry.getIconUrl(manifest)
    }

    override suspend fun readmeUrl(): String? {
        return ExtensionRegistry.getReadmeUrl(manifest)
    }

    override suspend fun getRating() = null

    override suspend fun getReviews(): List<Review> = emptyList()

    override suspend fun getDownloadCount() = null
}

data class UpdatableExtension(val installed: LocalExtension, val store: StoreExtension) : Extension {
    override val id
        get() = installed.id

    override val name
        get() = installed.name

    override val version
        get() = installed.version

    override val authors
        get() = installed.authors

    override val description
        get() = installed.description

    override val tags
        get() = installed.tags

    override val repository
        get() = installed.repository

    override val license
        get() = installed.license

    override suspend fun iconUrl(): String? = installed.iconUrl()

    override suspend fun readmeUrl(): String? = installed.readmeUrl()

    override suspend fun getRating() = store.getRating()

    override suspend fun getReviews() = store.getReviews()

    override suspend fun getDownloadCount() = store.getDownloadCount()
}

fun LocalExtension.classLoader(parent: ClassLoader?) = PathClassLoader(apkFile.absolutePath, parent)

val LocalExtension.apkFile
    get() = run {
        val dir = File(installPath)

        if (!dir.isDirectory) error("Extension [$name, $id] directory not found")

        dir.listFiles { it.extension == "apk" }?.first()?.also { it.setReadOnly() } ?: error("APK not found")
    }

fun LocalExtension.getApkPackageInfo(context: Context) = run {
    val pm = context.packageManager
    pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES)!!
        .apply {
            applicationInfo!!.sourceDir = apkFile.absolutePath
            applicationInfo!!.publicSourceDir = apkFile.absolutePath
        }
}
