package com.rk

import androidx.core.content.pm.PackageInfoCompat
import com.rk.libcommons.application
import com.rk.file.localBinDir
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings

object UpdateManager {
    private fun deleteCommonFiles() = with(application!!) {
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

    fun inspect() = with(application!!) {
        val lastVersionCode = Settings.lastVersionCode
        val currentVersionCode =
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

        if (lastVersionCode != currentVersionCode) {
            // App is updated -> Migrate existing files
            when (lastVersionCode) {
                -1L -> {
                    deleteCommonFiles()
                }

                40L -> {
                    Preference.clearData()
                    deleteCommonFiles()
                }

                48L -> {
                    deleteCommonFiles()
                }

                66L -> {
                    if (Settings.line_spacing == 0f) Settings.line_spacing = 1f
                    deleteCommonFiles()
                }

                else -> {
                    deleteCommonFiles()
                }
            }

        }

        Settings.lastVersionCode = currentVersionCode
    }
}
