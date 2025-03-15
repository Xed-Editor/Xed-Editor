package com.rk.libcommons

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext


class LoadingPopup @OptIn(DelicateCoroutinesApi::class) constructor(private val ctx: Context, hideAfterMillis: Long? = null, scope: CoroutineScope = GlobalScope) {
    private var dialog: AlertDialog? = null
    private lateinit var dialogView: View

    init {
        val code = {
            val inflater1: LayoutInflater = LayoutInflater.from(ctx)
            dialogView = inflater1.inflate(R.layout.progress_dialog, null)
            dialogView.findViewById<TextView>(R.id.progress_message).text = strings.wait.getString()
            dialog =
                MaterialAlertDialogBuilder(ctx).setView(dialogView).setCancelable(false).create()

            if (hideAfterMillis != null) {
                show()
                scope.launch {
                    delay(hideAfterMillis)
                    withContext(Dispatchers.Main){
                        hide()
                    }
                }
            }
        }
        if (isMainThread().not()){
            runBlocking(Dispatchers.Main){
                code.invoke()
            }
        }else{
            code.invoke()
        }
    }

    fun setMessage(message: String): LoadingPopup {
        dialogView.findViewById<TextView>(R.id.progress_message).text = message
        return this
    }

    fun show(): LoadingPopup {
        runOnUiThread {
            if (dialog?.isShowing?.not() == true) {
                dialog?.show()
            }
        }
        return this
    }

    fun hide() {
        runOnUiThread {
            if (dialog != null && dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        }
    }

    fun getDialog(): AlertDialog? {
        return dialog
    }
}
