package com.rk.libcommons

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import com.rk.xededitor.ui.theme.XedTheme

class LoadingPopup @OptIn(DelicateCoroutinesApi::class) constructor(
    private val ctx: Context,
    hideAfterMillis: Long? = null,
    scope: CoroutineScope = GlobalScope
) {
    private var dialog: AlertDialog? = null
    private var message: String = strings.wait.getString()

    init {
        val code = {
            dialog = MaterialAlertDialogBuilder(ctx)
                .setView(createComposeView())
                .setCancelable(false)
                .create()

            if (hideAfterMillis != null) {
                show()
                scope.launch {
                    delay(hideAfterMillis)
                    withContext(Dispatchers.Main) {
                        hide()
                    }
                }
            }
        }
        if (isMainThread().not()) {
            runBlocking(Dispatchers.Main) {
                code.invoke()
            }
        } else {
            code.invoke()
        }
    }

    private fun createComposeView(): android.view.View {
        return ComposeView(ctx).apply {
            setContent {
                XedTheme {
                    Surface {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }

    fun setMessage(message: String): LoadingPopup {
        this.message = message
        dialog?.setView(createComposeView())
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