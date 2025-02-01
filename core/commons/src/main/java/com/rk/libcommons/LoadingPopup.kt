package com.rk.libcommons

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


class LoadingPopup(private val ctx: Activity, hideAfterMillis: Long? = null,val scope: CoroutineScope = DefaultScope) {
    private var dialog: AlertDialog? = null
    private lateinit var dialogView: View
    private lateinit var progressBar:ProgressBar
    private lateinit var textView: TextView
    private val mutex = Mutex(locked = true)

    private fun createProgressView(context: Activity): LinearLayout {
        val linearLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(5, 5, 5, 5)
        }

        progressBar = ProgressBar(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(5, 5, 5, 5)
            }
        }

        textView = TextView(context).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            gravity = Gravity.CENTER_VERTICAL
            text = strings.wait.getString()
        }

        linearLayout.addView(progressBar)
        linearLayout.addView(textView)

        return linearLayout
    }


    init {
        scope.launch(Dispatchers.Main){
            dialogView = createProgressView(ctx)
            dialog = MaterialAlertDialogBuilder(ctx).setView(dialogView).setCancelable(false).create()

            if (hideAfterMillis != null) {
                show()
                scope.launch {
                    delay(hideAfterMillis)
                    withContext(Dispatchers.Main){
                        hide()
                    }
                }
            }
            mutex.unlock()
        }
    }

    fun setMessage(message: String): LoadingPopup {
        scope.launch(Dispatchers.Main) {
            mutex.withLock {
                textView.text = message
            }
        }
       return this
    }

    fun show(): LoadingPopup {
        scope.launch(Dispatchers.Main) {
            mutex.withLock {
                if (dialog?.isShowing?.not() == true) {
                    dialog?.show()
                }
            }

        }

        return this
    }

    fun hide() {
        scope.launch(Dispatchers.Main){
            mutex.withLock {
                if (dialog != null && dialog?.isShowing == true) {
                    dialog?.dismiss()
                }
            }

        }
    }
}
