package com.rk.xededitor.MainActivity.handlers.git

import android.app.Activity
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.askSomething
import com.rk.xededitor.rkUtils.runOnUiThread
import com.rk.xededitor.rkUtils.toastIt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

fun push(activity: Activity,file:File) {
    activity.askSomething(
        title = "Push?",
        message = "Are you sure you want to push?",
        buttons = Pair(rkUtils.PopupButton(label = "No", null), rkUtils.PopupButton(label = "Yes", listener = {
            DefaultScope.launch(Dispatchers.IO) {
                val root = FileManager.findGitRoot(file)
                if (root != null) {
                    val loading = LoadingPopup(activity,null).show()
                    GitClient.push(activity,root, onResult = {
                        runOnUiThread{
                            loading.hide()
                        }
                        it?.message?.toastIt()
                    })
                }else{
                    rkUtils.toast("Unable to find git root")
                }
            }
        })),
    )
}