package com.rk.extension

import android.content.Context
import android.content.pm.PackageManager
import dalvik.system.PathClassLoader
import java.io.File

sealed interface Extension {
    val id: ExtensionId
    val name: String
    val version: String
    val authors: List<String>
    val description: String
    val repository: String
}

/** Extensions that are published in the store (online registry). Might or might not be installed locally. */
data class StoreExtension(val info: PluginInfo, val verified: Boolean = false) : Extension {
    override val id
        get() = info.id

    override val name
        get() = info.name

    override val version
        get() = info.version

    override val authors
        get() = info.authors

    override val description
        get() = info.description

    override val repository
        get() = info.repository
}

/** Extensions that are installed locally (from disk). */
data class LocalExtension(
    val info: PluginInfo,

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
        get() = info.id

    override val name
        get() = info.name

    override val version
        get() = info.version

    override val authors
        get() = info.authors

    override val description
        get() = info.description

    override val repository
        get() = info.repository
}

data class UpdatableExtension(val installed: LocalExtension, val availableUpdate: StoreExtension) : Extension {
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

    override val repository
        get() = installed.repository
}

fun LocalExtension.classLoader(parent: ClassLoader?) = PathClassLoader(apkFile.absolutePath, parent)

val LocalExtension.apkFile
    get() = run {
        val dir = File(installPath)

        if (!dir.isDirectory) error("Extension [$name, $id] directory not found")

        dir.listFiles { it.extension == "apk" }?.first()?.also { it.setReadOnly() } ?: error("apk not found")
    }

fun LocalExtension.getApkPackageInfo(context: Context) = run {
    val pm = context.packageManager
    pm.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_META_DATA or PackageManager.GET_ACTIVITIES)!!
        .apply {
            applicationInfo!!.sourceDir = apkFile.absolutePath
            applicationInfo!!.publicSourceDir = apkFile.absolutePath
        }
}
