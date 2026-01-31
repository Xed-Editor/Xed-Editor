package com.rk.git

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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.channels.SendChannel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.rk.theme.XedTheme
import org.eclipse.jgit.lib.ProgressMonitor

class GitProgressMonitor(
    private val activity: AppCompatActivity?,
    private val messageChannel: SendChannel<String>? = null,
) : ProgressMonitor {

    private var cancelled = false
    private var dialog: AlertDialog? = null

    private var progress by mutableIntStateOf(0)
    private var maxProgress by mutableIntStateOf(0)
    private var message by mutableStateOf("")

    init {
        if (activity != null) {
            dialog = MaterialAlertDialogBuilder(activity)
                .setView(createComposeView())
                .setCancelable(false)
                .create()
            show()
        } else {
            Log.e(this::class.java.simpleName, "Activity is null; progress dialog will not show")
        }
    }

    private fun createComposeView(): ComposeView? {
        if (activity == null) return null

        return ComposeView(activity).apply {
            setContent {
                XedTheme {
                    Surface {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                progress = if (maxProgress > 0) progress.toFloat() / maxProgress else 0f,
                                modifier = Modifier.size(48.dp).padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    fun show() {
        activity?.runOnUiThread {
            if (dialog?.isShowing != true) {
                dialog?.show()
            }
        }
    }

    fun hide() {
        activity?.runOnUiThread {
            dialog?.let { if (it.isShowing) it.dismiss() }
        }
    }

    fun cancel() {
        cancelled = true
        hide()
    }

    override fun start(totalTasks: Int) {
        maxProgress = totalTasks
        progress = 0
        show()
    }

    override fun beginTask(title: String?, totalWork: Int) {
        message = title.orEmpty()
        messageChannel?.trySend(message)
        if (dialog?.isShowing == true) {
            dialog?.setView(createComposeView())
        }
        maxProgress = totalWork
        progress = 0
        show()
    }

    override fun update(completed: Int) {
        progress = completed
    }

    override fun endTask() {
        hide()
    }

    override fun isCancelled(): Boolean = cancelled || Thread.currentThread().isInterrupted
    override fun showDuration(enabled: Boolean) = Unit
}