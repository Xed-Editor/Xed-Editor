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
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.activities.main.MainActivity
import com.rk.extension.XedExtensionPoint
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.XedTheme

fun errorDialog(activity: Activity? = MainActivity.instance, title: String = strings.error.getString(), msg: String) {
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

        dialogRes(activity = activity, title = title, msg = msg, onOk = {})
    }
}

fun errorDialog(@StringRes msgRes: Int) {
    runOnUiThread { errorDialog(msg = msgRes.getString()) }
}

fun errorDialog(activity: Activity? = MainActivity.instance, throwable: Throwable) {
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

        errorDialog(activity = activity, msg = message.toString())
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

var isDialogShowing = false
    private set

fun dialogRes(
    activity: Activity? = MainActivity.instance,
    title: String? = null,
    msg: String,
    @StringRes cancelRes: Int = strings.cancel,
    @StringRes okRes: Int = strings.ok,
    onOk: (AlertDialog?) -> Unit = {},
    onCancel: ((AlertDialog?) -> Unit)? = null,
    cancelable: Boolean = true,
) {
    dialog(
        activity = activity,
        title = title,
        msg = msg,
        cancelText = cancelRes.getString(),
        okText = okRes.getString(),
        onOk = onOk,
        onCancel = onCancel,
        cancelable = cancelable,
    )
}

@XedExtensionPoint
fun dialog(
    activity: Activity? = MainActivity.instance,
    title: String? = null,
    msg: String,
    cancelText: String = strings.cancel.getString(),
    okText: String = strings.ok.getString(),
    onOk: (AlertDialog?) -> Unit = {},
    onCancel: ((AlertDialog?) -> Unit)? = null,
    cancelable: Boolean = true,
) {
    if (activity == null) {
        toast(strings.unknown_error)
        return
    }
    var alertDialog: AlertDialog? = null
    runOnUiThread {
        MaterialAlertDialogBuilder(activity).apply {
            setOnCancelListener { isDialogShowing = false }

            setView(
                ComposeView(activity).apply {
                    setContent {
                        XedTheme {
                            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                                alertDialog?.setCancelable(cancelable)
                                DialogContent(
                                    alertDialog = alertDialog,
                                    title = title,
                                    msg = msg,
                                    cancelString = cancelText,
                                    okString = okText,
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
            )

            if (activity.isFinishing || activity.isDestroyed) {
                toast(msg)
                return@runOnUiThread
            }

            alertDialog = show()
            isDialogShowing = true
        }
    }
}

@Composable
private fun DialogContent(
    alertDialog: AlertDialog?,
    title: String?,
    msg: String,
    cancelString: String,
    okString: String,
    onOk: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    Column(modifier = Modifier.padding(24.dp)) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }

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
                    Text(cancelString)
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            TextButton(
                onClick = {
                    alertDialog?.dismiss()
                    onOk()
                }
            ) {
                Text(okString)
            }
        }
    }
}

@XedExtensionPoint
fun composableDialog(
    activity: Activity? = MainActivity.instance,
    cancelable: Boolean = true,
    composable: @Composable (AlertDialog?) -> Unit,
) {
    if (activity == null) {
        toast(strings.unknown_error)
        return
    }
    var alertDialog: AlertDialog? = null
    runOnUiThread {
        MaterialAlertDialogBuilder(activity).apply {
            setOnCancelListener { isDialogShowing = false }

            setView(
                ComposeView(activity).apply {
                    setContent {
                        XedTheme {
                            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 1.dp) {
                                alertDialog?.setCancelable(cancelable)
                                composable(alertDialog)
                            }
                        }
                    }
                }
            )

            alertDialog = show()
            isDialogShowing = true
        }
    }
}
