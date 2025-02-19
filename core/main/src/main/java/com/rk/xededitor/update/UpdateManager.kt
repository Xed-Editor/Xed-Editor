package com.rk.xededitor.update

import androidx.core.content.pm.PackageInfoCompat
import com.rk.libcommons.application
import com.rk.libcommons.child
import com.rk.libcommons.localBinDir
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.settings.Preference
import com.rk.settings.Settings

object UpdateManager {
    private fun deleteCommonFiles() = with(application!!){
        Preference.clearData()

        cacheDir.apply {
            if(exists()){
                deleteRecursively()
            }
        }
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

    fun inspect() = with(application!!){
        val lastVersionCode = Settings.lastVersionCode
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0))

        if (lastVersionCode != currentVersionCode){
            //app is updated
            when(lastVersionCode){
                -1L -> {
                    deleteCommonFiles()
                }
                40L -> {
                    deleteCommonFiles()
                    toast(strings.update_files_cleared)
                }
                else -> {
                    deleteCommonFiles()
                }
            }

        }

        Settings.lastVersionCode = currentVersionCode

    }
}