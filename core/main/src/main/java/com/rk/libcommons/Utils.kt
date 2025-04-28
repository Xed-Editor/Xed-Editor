package com.rk.libcommons

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.components.compose.preferences.base.DividerColumn
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.BuildConfig
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.ui.theme.KarbonTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

@OptIn(DelicateCoroutinesApi::class)
fun postIO(block: suspend CoroutineScope.() -> Unit) {
    GlobalScope.launch(Dispatchers.IO, block = block)
}

suspend fun IO(block: suspend CoroutineScope.() -> Unit) {
    withContext(Dispatchers.IO, block)
}

suspend fun Default(block: suspend CoroutineScope.() -> Unit) {
    withContext(Dispatchers.Default, block)
}

suspend fun UI(block: suspend CoroutineScope.() -> Unit) {
    withContext(Dispatchers.Main, block)
}

inline fun CoroutineScope.safeLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(context = context) {
        runCatching { block() }.onFailure {
            it.printStackTrace();
            if (BuildConfig.DEBUG) {
                throw it
            }
        }
    }
}

inline fun CoroutineScope.safeToastLaunch(
    context: CoroutineContext = EmptyCoroutineContext,
    crossinline block: suspend CoroutineScope.() -> Unit
): Job {
    return launch(context = context) { toastCatching { block() } }
}


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

inline fun String?.toastIt() {
    toast(this)
}

inline fun toastCatching(block: () -> Unit): Exception? {
    try {
        block()
        return null
    } catch (e: Exception) {
        e.printStackTrace()
        errorDialog(e)
        if (BuildConfig.DEBUG) {
            throw e
        }
        return e
    }
}

fun isDarkMode(ctx: Context): Boolean {
    return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
}

inline fun dpToPx(dp: Float, ctx: Context): Int {
    val density = ctx.resources.displayMetrics.density
    return Math.round(dp * density)
}

inline fun isMainThread(): Boolean {
    return Thread.currentThread().name == "main"
}

enum class PopupButtonType{
    POSITIVE,NEGATIVE,NEUTRAL
}
data class PopupButton(val label: String, val listener: ((DialogInterface) -> Unit)? = null, val type: PopupButtonType = PopupButtonType.NEUTRAL)

fun Activity.askInput(
    title: String? = null,
    input: String? = null,
    hint: String,
    onResult: (String) -> Unit
) {
    val popupView: View = LayoutInflater.from(this).inflate(R.layout.popup_new, null)
    val editText = popupView.findViewById<EditText>(R.id.name)
    editText.hint = hint

    MaterialAlertDialogBuilder(this).apply {
        title?.let { setTitle(it) }
        input?.let { editText.setText(it) }
        setView(popupView)
        var dialog: AlertDialog? = null
        setNegativeButton(strings.cancel, null)
        setPositiveButton(strings.ok) { _, _ ->
            dialog?.dismiss()
            onResult.invoke(editText.text.toString())
        }
        dialog = show()
    }

}

fun dialog(context: Activity? = MainActivity.activityRef.get(), title: String?, msg: String?, onCancel:((DialogInterface)-> Unit)? = null, onOk:((DialogInterface)-> Unit)? = null){
    if (context == null){
        throw IllegalArgumentException("context cannot be null")
        return
    }
    runOnUiThread{
        MaterialAlertDialogBuilder(context).apply {
            title?.let { setTitle(it) }
            msg?.let { setMessage(it) }

            onCancel?.let { setNegativeButton(strings.cancel){ dialogInterface,_ ->
                onCancel(dialogInterface)
            } }

            onOk?.let { setPositiveButton(strings.ok){ dialogInterface,_ ->
                onOk(dialogInterface)
            } }

            show()
        }
    }
}

fun composeDialog(context: Activity? = MainActivity.activityRef.get(),content:@Composable (AlertDialog?) -> Unit){
    if (context == null){
        throw IllegalArgumentException("context cannot be null")
        return
    }
    var dialog: AlertDialog? = null
    runOnUiThread{
        MaterialAlertDialogBuilder(context).apply {
            setView(ComposeView(context).apply { setContent { KarbonTheme {
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
                        }}

                }

            } } })
            dialog = show()
        }
    }
}


fun errorDialog(msg: String){
    if (msg.isBlank()){
        Log.w("Utils Error function","Message is blank")
        return
    }

    val activity = MainActivity.activityRef.get()
    if (activity == null){
        toast(msg)
        return
    }

    dialog(title = strings.err.getString(), msg = msg, onOk = {})
}

fun errorDialog(@StringRes msgRes: Int){
    errorDialog(msg = msgRes.getString())
}


fun errorDialog(throwable: Throwable){
    var message = StringBuilder()
    throwable.let {
        message.append(it.message).append("\n")
        message.append(it.stackTraceToString()).append("\n")
    }

    errorDialog(msg = message.toString())
}

fun errorDialog(exception: Exception){
    var message = StringBuilder()
    exception.let {
        message.append(it.message).append("\n")
        message.append(it.stackTraceToString()).append("\n")
    }

    errorDialog(msg = message.toString())
}
