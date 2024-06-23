package com.rk.xededitor

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class Loading(val ctx: Activity, hide_after_millis: Long?) {
    private var dialog: AlertDialog? = null

    init {
        // Create the dialog on the UI thread
        ctx.runOnUiThread {
            val inflater1: LayoutInflater = ctx.layoutInflater
            val dialogView: View = inflater1.inflate(R.layout.progress_dialog, null)
            dialog = MaterialAlertDialogBuilder(ctx).setView(dialogView)
                .setCancelable(false).create()

            if (hide_after_millis != null) {
                show()
                After(hide_after_millis) {
                    ctx.runOnUiThread {
                        hide()
                    }
                }
            }
        }
    }

    fun show(): Loading {
        ctx.runOnUiThread {
            dialog?.show()
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
