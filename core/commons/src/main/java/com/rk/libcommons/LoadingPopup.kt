package com.rk.libcommons

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.strings

class LoadingPopup(private val ctx: Activity, hideAfterMillis: Long?) {
    private var dialog: AlertDialog? = null
    private lateinit var dialogView: View

    init {
        // Create the dialog on the UI thread
        ctx.runOnUiThread {
            val inflater1: LayoutInflater = ctx.layoutInflater
            dialogView = inflater1.inflate(R.layout.progress_dialog, null)
            dialogView.findViewById<TextView>(R.id.progress_message).text = ContextCompat.getString(ctx,strings.wait)
            dialog =
                MaterialAlertDialogBuilder(ctx).setView(dialogView).setCancelable(false).create()

            if (hideAfterMillis != null) {
                show()
                After(hideAfterMillis) { ctx.runOnUiThread { hide() } }
            }
        }
    }

    fun setMessage(message: String): LoadingPopup {
        dialogView.findViewById<TextView>(R.id.progress_message).text = message
        return this
    }

    fun show(): LoadingPopup {
        ctx.runOnUiThread {
            if (dialog?.isShowing?.not() == true) {
                dialog?.show()
            }
        }
        return this
    }

    fun hide() {
        ctx.runOnUiThread {
            if (dialog != null && dialog?.isShowing == true) {
                dialog?.dismiss()
            }
        }
    }

    fun getDialog(): AlertDialog? {
        return dialog
    }
}
