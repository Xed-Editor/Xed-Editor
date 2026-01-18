package com.rk.utils

import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.theme.XedTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LoadingPopup(private val activity: AppCompatActivity?, hideAfterMillis: Long? = null) {
    private var dialog: AlertDialog? = null
    private var message: String = strings.wait.getString()

    init {
        if (activity != null) {
            dialog = MaterialAlertDialogBuilder(activity).setView(createComposeView()).setCancelable(false).create()

            hideAfterMillis?.let { delayMillis ->
                show()
                activity.lifecycleScope.launch {
                    delay(delayMillis)
                    hide()
                }
            }
        } else {
            Log.e(this::class.java.simpleName, "Activity is null this loading popup will not show")
        }
    }

    private fun createComposeView(): android.view.View? {
        if (activity == null) {
            return null
        }
        return ComposeView(activity).apply {
            setContent {
                XedTheme {
                    Surface {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp).padding(8.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    fun setMessage(message: String): LoadingPopup {
        this.message = message
        if (dialog?.isShowing == true) {
            dialog?.setView(createComposeView())
        }
        return this
    }

    fun show(): LoadingPopup {
        if (activity != null) {
            if (activity.isFinishing || activity.isDestroyed) return this

            activity.runOnUiThread {
                if (dialog?.isShowing != true) {
                    dialog?.show()
                }
            }
        }

        return this
    }

    fun hide() {
        if (activity == null) {
            return
        }
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread { dialog?.let { if (it.isShowing) it.dismiss() } }
    }
}
