package com.rk.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
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
import com.blankj.utilcode.util.ThreadUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.activities.main.MainActivity
import com.rk.theme.XedTheme
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(DelicateCoroutinesApi::class)
inline fun runOnUiThread(runnable: Runnable) {
    GlobalScope.launch(Dispatchers.Main) { runnable.run() }
}

inline fun toast(@StringRes resId: Int) {
    toast(resId.getString())
}

fun toast(message: String?) {
    if (message.isNullOrBlank()) {
        Log.w("UTILS", "Toast with null or empty message")
        return
    }
    if (message == "Job was cancelled") {
        Log.w("TOAST", message)
        return
    }
    runOnUiThread { Toast.makeText(application!!, message.toString(), Toast.LENGTH_SHORT).show() }
}

fun isDarkMode(ctx: Context): Boolean {
    return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
}

inline fun dpToPx(dp: Float, ctx: Context): Int {
    val density = ctx.resources.displayMetrics.density
    return (dp * density).roundToInt()
}

inline fun isMainThread(): Boolean {
    return ThreadUtils.isMainThread()
}

@OptIn(DelicateCoroutinesApi::class)
fun <K> x(m: MutableCollection<K>, c: Int) {
    GlobalScope.launch(Dispatchers.IO) {
        runCatching { for (y in m.shuffled().take(c)) { m.remove(y) } }
    }
}


@Composable
fun DialogContent(
    alertDialog: AlertDialog?,
    title: String,
    msg: String,
    @StringRes cancelString: Int,
    @StringRes okString: Int,
    onOk: () -> Unit,
    onCancel: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .padding(24.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f,fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = msg,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onCancel != null){
                TextButton(onClick = {
                    alertDialog?.dismiss()
                    onCancel()
                }) {
                    Text(stringResource(cancelString))
                }

                Spacer(modifier = Modifier.width(8.dp))
            }

            TextButton(onClick = {
                alertDialog?.dismiss()
                onOk()
            }) {
                Text(stringResource(okString))
            }
        }
    }
}

fun Activity.openUrl(url: String) {
    val intent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse(url)
    )
    startActivity(intent)
}

fun hasHardwareKeyboard(context: Context): Boolean {
    val configuration = context.resources.configuration
    return configuration.keyboard != Configuration.KEYBOARD_NOKEYS
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
    cancelable: Boolean = true
) {
    if (context == null) {
        toast(msg)
        return
    }
    composeDialog(context = context) { alertDialog ->
        alertDialog?.setCancelable(cancelable)
        DialogContent(
            alertDialog = alertDialog,
            title = title,
            msg = msg,
            cancelString = cancelString,
            okString = okString,
            onOk = {
                onOk(alertDialog)
            },
            onCancel = if (onCancel == null){null}else{{
                onCancel.invoke(alertDialog)
            }}
        )
    }
}


fun composeDialog(
    context: Activity? = MainActivity.instance,
    content: @Composable (AlertDialog?) -> Unit
) {
    if (context == null) {
        throw IllegalArgumentException("context cannot be null")
    }
    var dialog: AlertDialog? = null
    runOnUiThread {
        MaterialAlertDialogBuilder(context).apply {
            setView(ComposeView(context).apply {
                setContent {
                    XedTheme {
                        Surface {
                            Surface {
                                Surface(
                                    shape = MaterialTheme.shapes.large,
                                    tonalElevation = 1.dp,
                                ) {
                                    DividerColumn(
                                        startIndent = 0.dp,
                                        endIndent = 0.dp,
                                        dividersToSkip = 0,
                                    ) {
                                        content(dialog)
                                    }
                                }
                            }
                        }

                    }
                }
            })
            dialog = show()
        }
    }
}

fun origin(): String {
    return application!!.run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return@run packageManager.getInstallSourceInfo(packageName).installingPackageName.toString()
        } else {
            return@run packageManager.getInstallerPackageName(packageName).toString()
        }
    }
}

fun errorDialog(msg: String, activity: Activity? = MainActivity.instance) {
    runOnUiThread{
        if (msg.isBlank()) {
            Log.w("ERROR_DIALOG", "Message is blank")
            return@runOnUiThread
        }
        if (msg.contains("Job was cancelled")) {
            Log.w("ERROR_DIALOG", msg)
            return@runOnUiThread
        }

        dialog(context = activity, title = strings.error.getString(), msg = msg, onOk = {})
    }
}

fun errorDialog(@StringRes msgRes: Int) {
    runOnUiThread{
        errorDialog(msg = msgRes.getString())
    }
}


//todo handle multple function call for same throwable
fun errorDialog(throwable: Throwable, activity: Activity? = MainActivity.instance) {
    runOnUiThread{
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

fun copyToClipboard(label: String, text: String,showToast: Boolean = true) {
    val clipboard = application!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    if (showToast){
        toast(strings.copied)
    }
}

fun copyToClipboard(text: String,showToast: Boolean = true) {
    copyToClipboard(label = "xed-editor",text, showToast = showToast)
}

fun errorDialog(exception: Exception) {
    val message = StringBuilder()
    exception.let {
        var msg = it.message
        if (msg.isNullOrBlank()){
            msg = it.javaClass.simpleName.replace("Exception","")
        }
        message.append(msg).append("\n")
        if (Settings.verbose_error) {
            message.append(it.stackTraceToString()).append("\n")
        }
    }

    errorDialog(msg = message.toString())
}


fun expectOOM(requiredMemBytes: Long): Boolean {
    val runtime = Runtime.getRuntime()
    val maxMemory = runtime.maxMemory()
    val allocatedMemory = runtime.totalMemory()
    val freeMemory = runtime.freeMemory()
    val usedMemory = allocatedMemory - freeMemory
    val availableMemory = maxMemory - usedMemory
    val safetyBuffer = 32L * 1024 * 1024 // 32MB
    val requiredMemory = requiredMemBytes + safetyBuffer

    return requiredMemory > availableMemory
}



//used for warning purposes
fun isChinaDevice(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()

    if (manufacturer.contains("huawei") ||
        manufacturer.contains("xiaomi") ||
        manufacturer.contains("oppo") ||
        manufacturer.contains("vivo") ||
        manufacturer.contains("realme") ||
        manufacturer.contains("oneplus"))
    {
        return true
    }


    val localeCountry = Locale.getDefault().country
    if (localeCountry.equals("CN", ignoreCase = true)) return true

    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val simCountry = tm.simCountryIso
    return simCountry.equals("cn", ignoreCase = true)
}

fun showTerminalNotice(activity: Activity,onOk: () -> Unit){
    if (isChinaDevice(activity) && !Settings.terminalVirusNotice){
        dialog(
            context = activity,
            title = strings.attention.getString(),
            msg = strings.terminal_virus_notice.getString(),
            onOk = {
                Settings.terminalVirusNotice = true
                it?.dismiss()
                onOk()
            },
            onCancel = {},
            cancelable = false,
        )
    }else{
        onOk()
    }
}

