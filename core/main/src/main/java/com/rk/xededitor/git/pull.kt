package com.rk.xededitor.git

import android.app.Activity
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.git.GitClient
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
                toast(it)
            })
        }else{
            toast("Unable to find git root")
        }
    }
}