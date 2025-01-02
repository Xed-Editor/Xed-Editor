package com.rk.xededitor.MainActivity.handlers.git

import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.R
import com.rk.xededitor.git.GitClient
import com.rk.xededitor.rkUtils
import com.rk.xededitor.rkUtils.askInput
import com.rk.xededitor.rkUtils.runOnUiThread
import com.rk.xededitor.rkUtils.toastIt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

fun commit(mainActivity: Activity, file: File){

    mainActivity.askInput(
        title = "Commit",
        message = "Commit Message",
        onResult = {
            DefaultScope.launch(Dispatchers.IO) {
                var loading:LoadingPopup? = null

                withContext(Dispatchers.Main){
                    loading = LoadingPopup(mainActivity,null).show()
                }
                val root = FileManager.findGitRoot(file)
                if (root != null) {
                    GitClient.commit(mainActivity,root, message = it, onResult = {
                        runOnUiThread{
                            loading!!.hide()
                        }
                        it?.message?.toastIt()
                    })
                }else{
                    rkUtils.toast("Unable to find git root")
                }
            }
        },
    )
}