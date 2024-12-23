package com.rk.xededitor.MainActivity.handlers.git

import android.app.Activity
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.runOnUiThread
import com.rk.xededitor.rkUtils.toastIt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun pull(activity: Activity,file: File){
    DefaultScope.launch(Dispatchers.IO) {
        val root = FileManager.findGitRoot(file)
        if (root != null) {
            val loading = LoadingPopup(activity,null).show()
            GitClient.pull(activity,root, onResult = {
                runOnUiThread{
                    loading.hide()
                }
                it?.message?.toastIt()
            })
        }else{
            rkUtils.toast("Unable to find git root")
        }
    }
}