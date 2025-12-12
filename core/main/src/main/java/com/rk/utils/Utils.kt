package com.rk.utils

import android.annotation.SuppressLint
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
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.blankj.utilcode.util.ThreadUtils
import com.rk.file.FileObject
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(DelicateCoroutinesApi::class)
inline fun runOnUiThread(runnable: Runnable) {
    GlobalScope.launch(Dispatchers.Main) { runnable.run() }
}

inline fun toast(@StringRes resId: Int) {
    toast(resId.getString())
}

suspend fun FileObject.writeObject(obj: Any) =
    withContext(Dispatchers.IO) { ObjectOutputStream(getOutPutStream(false)).use { oos -> oos.writeObject(obj) } }

suspend fun FileObject.readObject(): Any? =
    withContext(Dispatchers.IO) {
        ObjectInputStream(getInputStream()).use { ois ->
            return@withContext ois.readObject()
        }
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

fun getTempDir(): File {
    val tmp = File(application!!.filesDir.parentFile, "tmp")
    if (!tmp.exists()) {
        tmp.mkdir()
    }
    return tmp
}

val isFDroid by lazy {
    val targetSdkVersion = application!!.applicationInfo.targetSdkVersion
    targetSdkVersion == 28
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

private var selectionColor = Color.Unspecified

@SuppressLint("ComposableNaming")
@Composable
fun preloadSelectionColor() {
    val selectionColors = LocalTextSelectionColors.current
    val selectionBackground = selectionColors.backgroundColor
    selectionColor = selectionBackground
}

fun getSelectionColor(): Color {
    return selectionColor
}

// Helper function copied from
// https://github.com/MohamedRejeb/compose-dnd/blob/65d48ed0f0bd83a0b01263b7e046864bdd4a9048/sample/common/src/commonMain/kotlin/utils/ScrollUtils.kt
suspend fun handleLazyListScroll(lazyListState: LazyListState, dropIndex: Int): Unit = coroutineScope {
    val firstVisibleItemIndex = lazyListState.firstVisibleItemIndex
    val firstVisibleItemScrollOffset = lazyListState.firstVisibleItemScrollOffset

    // Workaround to fix scroll issue when dragging the first item
    if (dropIndex == 0 || dropIndex == 1) {
        launch { lazyListState.scrollToItem(firstVisibleItemIndex, firstVisibleItemScrollOffset) }
    }

    // Animate scroll when entering the first or last item
    val lastVisibleItemIndex = lazyListState.firstVisibleItemIndex + lazyListState.layoutInfo.visibleItemsInfo.lastIndex

    val firstVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull() ?: return@coroutineScope
    val scrollAmount = firstVisibleItem.size * 2f

    if (dropIndex <= firstVisibleItemIndex + 1) {
        launch { lazyListState.animateScrollBy(-scrollAmount) }
    } else if (dropIndex == lastVisibleItemIndex) {
        launch { lazyListState.animateScrollBy(scrollAmount) }
    }
}
