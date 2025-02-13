package com.rk.xededitor.git

import android.app.Activity
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.askInput
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.resources.strings
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.R
import com.rk.xededitor.git.GitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File




fun commit(mainActivity: Activity, file: File){

    mainActivity.askInput(
        title = "Commit",
        hint = "Commit Message",
        onResult = {
            DefaultScope.launch(Dispatchers.IO) {
                var loading:LoadingPopup?

                withContext(Dispatchers.Main){
                    loading = LoadingPopup(mainActivity,null).show()
                }
                val root = FileManager.findGitRoot(file)
                if (root != null) {
                    GitClient.commit(mainActivity,root, message = it, onResult = {
                        runOnUiThread{
                            loading!!.hide()
                        }
                        toast(it)
                    })
                }else{
                    toast("Unable to find git root")
                }
            }
        },
    )
}