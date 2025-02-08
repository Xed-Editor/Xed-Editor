package com.rk.xededitor.git

import android.app.Activity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.DefaultScope
import com.rk.libcommons.LoadingPopup
import com.rk.libcommons.PopupButton
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.xededitor.MainActivity.file.FileManager
import com.rk.xededitor.git.GitClient

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

private fun Activity.askSomething(title:String,message: String,buttons:Pair<PopupButton?,PopupButton?>?){
    runOnUiThread {
        var dialog: androidx.appcompat.app.AlertDialog? = null
        MaterialAlertDialogBuilder(this).apply {
            setTitle(title)
            setMessage(message)
            buttons?.first?.let {
                setNegativeButton(buttons.first?.label) { _, _ ->
                    dialog?.dismiss()
                    buttons.first?.listener?.invoke()
                }
            }

            buttons?.second?.let {
                setPositiveButton(buttons.second?.label) { _, _ ->
                    dialog?.dismiss()
                    buttons.second?.listener?.invoke()
                }
            }
            dialog = show()
        }
    }
}


fun push(activity: Activity,file:File) {
    activity.askSomething(
        title = "Push?",
        message = "Are you sure you want to push?",
        buttons = Pair(PopupButton(label = "No", null), PopupButton(label = "Yes", listener = {
            DefaultScope.launch(Dispatchers.IO) {
                val root = FileManager.findGitRoot(file)
                if (root != null) {
                    val loading = LoadingPopup(activity,null).show()
                    GitClient.push(activity,root, onResult = {
                        runOnUiThread{
                            loading.hide()
                        }
                        toast(it)
                    })
                }else{
                    toast("Unable to find git root")
                }
            }
        })),
    )
}