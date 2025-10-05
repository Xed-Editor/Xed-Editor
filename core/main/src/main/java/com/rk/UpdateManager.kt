package com.rk

import androidx.core.content.pm.PackageInfoCompat
import com.rk.libcommons.application
import com.rk.file.localBinDir
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings

object UpdateManager {
    private suspend fun deleteCommonFiles() = with(application!!){
        codeCacheDir.apply {
            if (exists()){
                deleteRecursively()
            }
        }

        localBinDir().apply {
            if (exists()){
                deleteRecursively()
            }
        }
    }

    suspend fun inspect() = with(application!!){
        val lastVersionCode = Settings.lastVersionCode
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

        if (lastVersionCode != currentVersionCode){
            //app is updated
            when(lastVersionCode){
                //what to do if the last version code matches this

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
                else -> {
                    deleteCommonFiles()
                }
            }

        }

        Settings.lastVersionCode = currentVersionCode

    }
}
