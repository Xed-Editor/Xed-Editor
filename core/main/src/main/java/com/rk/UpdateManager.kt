package com.rk

import androidx.core.content.pm.PackageInfoCompat
import com.rk.file.child
import com.rk.file.localBinDir
import com.rk.file.localDir
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.settings.Preference
import com.rk.settings.Settings
import com.rk.utils.application

object UpdateManager {
    private fun deleteCommonFiles() =
        with(application!!) {
            codeCacheDir.apply {
                if (exists()) {
                    deleteRecursively()
                }
            }

            localBinDir().apply {
                if (exists()) {
                    deleteRecursively()
                }
            }
        }

    fun inspect() =
        with(application!!) {
            val lastVersionCode = Settings.lastVersionCode
            val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

            if (lastVersionCode != currentVersionCode) {
                // App is updated -> Migrate existing files
                if (lastVersionCode <= 40L) {
                    Preference.clearData()
                }

                if (lastVersionCode <= 66L) {
                    Settings.line_spacing = 1f
                }

                if (lastVersionCode <= 68L) {
                    val rootfs =
                        sandboxDir().listFiles()?.filter {
                            it.absolutePath != sandboxHomeDir().absolutePath &&
                                it.absolutePath != sandboxDir().child("tmp").absolutePath
                        } ?: emptyList()

                    if (rootfs.isNotEmpty()) {
                        localDir().child(".terminal_setup_ok_DO_NOT_REMOVE").createNewFile()
                    }
                }

                if (lastVersionCode <= 69L) {
                    sandboxDir().child(".cache/.packages_ensured").apply {
                        if (exists()) {
                            delete()
                        }
                    }
                }

                if (lastVersionCode <= 73) {
                    runCatching {
                        val filesToCopy = application!!.cacheDir.listFiles { it.isFile && it.extension.isNotEmpty() }
                        filesToCopy?.forEach { it.copyTo(application!!.filesDir.child(it.name), overwrite = true) }
                    }
                }

                deleteCommonFiles()
            }

            Settings.lastVersionCode = currentVersionCode
        }
}
