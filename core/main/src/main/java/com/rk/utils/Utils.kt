package com.rk.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import android.telephony.TelephonyManager
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.Log
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.blankj.utilcode.util.ThreadUtils
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

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
    return ((ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES)
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
        runCatching {
            for (y in m.shuffled().take(c)) {
                m.remove(y)
            }
        }
    }
}

fun Activity.openUrl(url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    startActivity(intent)
}

fun hasHardwareKeyboard(context: Context): Boolean {
    val configuration = context.resources.configuration
    return configuration.keyboard != Configuration.KEYBOARD_NOKEYS
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

fun copyToClipboard(label: String, text: String, showToast: Boolean = true) {
    val clipboard = application!!.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    if (showToast) {
        toast(strings.copied)
    }
}

fun copyToClipboard(text: String, showToast: Boolean = true) {
    copyToClipboard(label = "xed-editor", text, showToast = showToast)
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

// used for warning purposes
fun isChinaDevice(context: Context): Boolean {
    val manufacturer = Build.MANUFACTURER.lowercase()

    if (
        manufacturer.contains("huawei") ||
            manufacturer.contains("xiaomi") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("vivo") ||
            manufacturer.contains("realme") ||
            manufacturer.contains("oneplus")
    ) {
        return true
    }

    val localeCountry = Locale.getDefault().country
    if (localeCountry.equals("CN", ignoreCase = true)) return true

    val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    val simCountry = tm.simCountryIso
    return simCountry.equals("cn", ignoreCase = true)
}

fun showTerminalNotice(activity: Activity, onOk: () -> Unit) {
    if (isChinaDevice(activity) && !Settings.terminalVirusNotice) {
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
    } else {
        onOk()
    }
}

fun isAppInstalled(context: Context, packageName: String): Boolean {
    return try {
        context.packageManager.getPackageInfo(packageName, 0)
        true // App is installed
    } catch (e: PackageManager.NameNotFoundException) {
        false // App not found
    }
}

fun getSourceDirOfPackage(context: Context, packageName: String): String? {
    return try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        info.sourceDir
    } catch (e: PackageManager.NameNotFoundException) {
        null // App not found
    }
}

/** Converts a [Spanned] text object to an [AnnotatedString]. */
fun Spanned.toAnnotatedString(): AnnotatedString {
    val builder = AnnotatedString.Builder(this.toString())
    val spans = getSpans(0, length, Any::class.java)
    spans.forEach { span ->
        val start = getSpanStart(span)
        val end = getSpanEnd(span)
        val style =
            when (span) {
                is ForegroundColorSpan -> SpanStyle(color = androidx.compose.ui.graphics.Color(span.foregroundColor))
                is StyleSpan ->
                    when (span.style) {
                        Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                        Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                        Typeface.BOLD_ITALIC -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                        else -> null
                    }
                is UnderlineSpan -> SpanStyle(textDecoration = TextDecoration.Underline)
                is StrikethroughSpan -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                else -> null
            }
        if (style != null) {
            builder.addStyle(style, start, end)
        }
    }
    return builder.toAnnotatedString()
}
