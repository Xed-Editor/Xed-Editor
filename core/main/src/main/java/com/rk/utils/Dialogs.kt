package com.rk.utils

import android.app.Activity
import android.util.Log
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.activities.main.MainActivity
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.XedTheme

fun errorDialog(msg: String, activity: Activity? = MainActivity.instance, title: String = strings.error.getString()) {
    Log.e("ERROR_DIALOG", msg)

    runOnUiThread {
        if (msg.isBlank()) {
            Log.w("ERROR_DIALOG", "Message is blank")
            return@runOnUiThread
        }
        if (msg.contains("Job was cancelled")) {
            Log.w("ERROR_DIALOG", msg)
            return@runOnUiThread
        }

        dialog(context = activity, title = title, msg = msg, onOk = {})
    }
}

fun errorDialog(@StringRes msgRes: Int) {
    runOnUiThread { errorDialog(msg = msgRes.getString()) }
}

// todo handle multple function call for same throwable
fun errorDialog(throwable: Throwable, activity: Activity? = MainActivity.instance) {
    runOnUiThread {
        if (throwable.message.toString().contains("Job was cancelled")) {
            Log.w("ERROR_DIALOG", throwable.message.toString())
            return@runOnUiThread
        }
        val message = StringBuilder()
        throwable.let {
            message.append(it.message).append("\n")
            if (Settings.verbose_error) {
                message.append(it.stackTraceToString()).append("\n")
            }
        }

        errorDialog(msg = message.toString(), activity = activity)
    }
}

fun errorDialog(exception: Exception) {
    val message = StringBuilder()
    exception.let {
        var msg = it.message
        if (msg.isNullOrBlank()) {
            msg = it.javaClass.simpleName.replace("Exception", "")
        }
        message.append(msg).append("\n")
        if (Settings.verbose_error) {
            message.append(it.stackTraceToString()).append("\n")
        }
    }

    errorDialog(msg = message.toString())
}

fun dialog(
    context: Activity? = MainActivity.instance,
    title: String,
    msg: String,
    @StringRes cancelString: Int = strings.cancel,
    @StringRes okString: Int = strings.ok,
    onDialog: (AlertDialog?) -> Unit = {},
    onOk: (AlertDialog?) -> Unit = {},
    onCancel: ((AlertDialog?) -> Unit)? = null,
    cancelable: Boolean = true,
) {
    if (context == null) {
        toast(msg)
        return
    }
    var alertDialog: AlertDialog? = null
    runOnUiThread {
        MaterialAlertDialogBuilder(context).apply {
            setView(
                ComposeView(context).apply {
                    setContent {
                        XedTheme {
                            Surface {
                                Surface {
                                    Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                                        DividerColumn(startIndent = 0.dp, endIndent = 0.dp, dividersToSkip = 0) {
                                            alertDialog?.setCancelable(cancelable)
                                            DialogContent(
                                                alertDialog = alertDialog,
                                                title = title,
                                                msg = msg,
                                                cancelString = cancelString,
                                                okString = okString,
                                                onOk = { onOk(alertDialog) },
                                                onCancel =
                                                    if (onCancel == null) {
                                                        null
                                                    } else {
                                                        { onCancel.invoke(alertDialog) }
                                                    },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )

            if (context.isFinishing || context.isDestroyed) {
                toast(msg)
                return@runOnUiThread
            }

            alertDialog = show()
        }
    }
}

@Composable
private fun DialogContent(
    alertDialog: AlertDialog?,
    title: String,
    msg: String,
    @StringRes cancelString: Int,
    @StringRes okString: Int,
    onOk: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            Text(text = msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 24.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onCancel != null) {
                TextButton(
                    onClick = {
                        alertDialog?.dismiss()
                        onCancel()
                    }
                ) {
                    Text(stringResource(cancelString))
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            TextButton(
                onClick = {
                    alertDialog?.dismiss()
                    onOk()
                }
            ) {
                Text(stringResource(okString))
            }
        }
    }
}
