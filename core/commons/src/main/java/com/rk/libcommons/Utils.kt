package com.rk.libcommons

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.resources.getString
import com.rk.resources.strings
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

inline fun toast(e: Exception? = null) {
    e?.printStackTrace()
    if (e != null) {
        toast(e.message)
    }
}

inline fun toast(t: Throwable? = null) {
    t?.printStackTrace()
    toast(t?.message)
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
        toast(e.message)
        if (BuildConfig.DEBUG) {
            throw e
        }
        return e
    }
}


inline fun dpToPx(dp: Float, ctx: Context): Int {
    val density = ctx.resources.displayMetrics.density
    return Math.round(dp * density)
}

inline fun isMainThread(): Boolean {
    return Thread.currentThread().name == "main"
}

data class PopupButton(val label: String, val listener: (() -> Unit)? = null)

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
