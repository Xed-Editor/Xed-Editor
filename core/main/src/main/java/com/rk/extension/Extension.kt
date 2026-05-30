package com.rk.extension

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import io.github.z4kn4fein.semver.toVersionOrNull
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

data class ExtensionStats(val downloadCount: Int?, val rating: Float?, val size: Long?)

sealed interface Extension {
    val id: ExtensionId
    val name: String
    val version: String
    val author: ExtensionAuthor
    val description: String?
    val tags: List<String>
    val repository: String
    val license: String?
    val hasSettings: Boolean

    val iconUrl: String

    val readmeUrl: String
    val changelogUrl: String

    val minAppVersion: Int?

    val maxAppVersion: Int?

    suspend fun getStats(): ExtensionStats

    suspend fun getReviews(): List<Review>
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

    override val hasSettings: Boolean
        get() = manifest.hasSettings

    override val iconUrl: String
        get() = ExtensionRegistry.getIconUrl(manifest.id)

    override val readmeUrl: String
        get() = ExtensionRegistry.getReadmeUrl(manifest.id)

    override val changelogUrl
        get() = ExtensionRegistry.getChangelogUrl(manifest.id)

    override val minAppVersion
        get() = manifest.minAppVersion

    override val maxAppVersion
        get() = manifest.maxAppVersion

    override suspend fun getStats() = ExtensionRegistry.getStats(manifest.id)

    override suspend fun getReviews(): List<Review> = emptyList()
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

    override val maxAppVersion
        get() = manifest.maxAppVersion

    override suspend fun getStats(): ExtensionStats {
        return ExtensionStats(null, null, calcSize())
    }

    private suspend fun calcSize(): Long {
        return withContext(Dispatchers.IO) {
            var totalSize = 0L
            val stack = ArrayDeque<File>()
            stack.add(File(installPath))

            loop@ while (stack.isNotEmpty()) {
                val current = stack.removeLast()
                runCatching {
                    if (current.isDirectory()) {
                        val files = current.listFiles() ?: continue@loop
                        stack.addAll(files)
                    } else {
                        totalSize += current.length()
                    }
                }
            }
            totalSize
        }
    }

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

    override val hasSettings: Boolean
        get() = installed.hasSettings

    override val iconUrl
        get() = if (isUpdatable()) store.iconUrl else installed.iconUrl

    override val readmeUrl
        get() = if (isUpdatable()) store.readmeUrl else installed.readmeUrl

    override val changelogUrl
        get() = if (isUpdatable()) store.changelogUrl else installed.changelogUrl

    override val minAppVersion
        get() = store.minAppVersion

    override val maxAppVersion
        get() = store.maxAppVersion

    override suspend fun getStats() = store.getStats()

    override suspend fun getReviews() = store.getReviews()

    fun isUpdatable(): Boolean {
        val installedVersion = installed.version.toVersionOrNull() ?: return false
        val storeVersion = store.version.toVersionOrNull() ?: return false
        return installedVersion < storeVersion
    }
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
