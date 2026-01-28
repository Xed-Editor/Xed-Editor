package com.rk.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
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
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.blankj.utilcode.util.ThreadUtils
import com.rk.file.FileObject
import com.rk.filetree.FileTreeViewModel
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.currentTheme
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
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
    runOnUiThread { Toast.makeText(application!!, message, Toast.LENGTH_SHORT).show() }
}

/** Returns true if the currently selected user theme is dark. If it's set to system, the system theme is used. */
fun isDarkTheme(ctx: Context): Boolean {
    return when (Settings.theme_mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> isSystemInDarkTheme(ctx)
    }
}

/** Returns true if the system theme is dark. **NOTE:** Prefer [isDarkTheme] to respect user settings. */
fun isSystemInDarkTheme(ctx: Context): Boolean {
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
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
    copyToClipboard(label = "Xed-Editor", text, showToast = showToast)
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
    if (isChinaDevice(activity) && !Settings.terminal_virus_notice) {
        dialog(
            context = activity,
            title = strings.attention.getString(),
            msg = strings.terminal_virus_notice.getString(),
            onOk = {
                Settings.terminal_virus_notice = true
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
                is ForegroundColorSpan -> SpanStyle(color = Color(span.foregroundColor))
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

@Composable
fun getUnderlineColor(context: Context, fileTreeViewModel: FileTreeViewModel, file: FileObject?): Color? {
    val diagnosticSeverity = file?.let { fileTreeViewModel.getNodeSeverity(it) } ?: -1
    val editorColors =
        if (isDarkTheme(context)) {
            currentTheme.value?.darkEditorColors
        } else {
            currentTheme.value?.lightEditorColors
        }
    val underlineColor =
        when (diagnosticSeverity) {
            1 -> {
                editorColors?.find { it.key == EditorColorScheme.PROBLEM_TYPO }?.color?.let { Color(it) }
                    ?: Color(0x6600ff11) // TODO: Change to green/yellow status colors later in LSP PR
            }
            2 -> {
                editorColors?.find { it.key == EditorColorScheme.PROBLEM_WARNING }?.color?.let { Color(it) }
                    ?: Color(0xaafff100) // TODO: Change to green/yellow status colors later in LSP PR
            }
            3 -> {
                editorColors?.find { it.key == EditorColorScheme.PROBLEM_ERROR }?.color?.let { Color(it) }
                    ?: MaterialTheme.colorScheme.error
            }
            else -> null
        }

    return underlineColor
}

fun Modifier.drawErrorUnderline(errorColor: Color): Modifier = drawBehind {
    val strokeWidth = 3f
    val waveOffset = 5f
    val waveHeight = 6f
    val waveLength = 20f

    val path = Path()
    var x = 0f
    val y = size.height + waveOffset - strokeWidth
    var up = true

    path.moveTo(0f, y)

    while (x < size.width) {
        val remaining = size.width - x
        val segment = minOf(waveLength / 2, remaining)

        val controlX = x + segment / 2
        val endX = x + segment

        val controlY = if (up) y - waveHeight else y + waveHeight

        path.quadraticTo(controlX, controlY, endX, y)

        up = !up
        x = endX
    }

    drawPath(path = path, color = errorColor, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}

fun isGitRepo(path: String): Boolean {
    var dir: File? = File(path)
    while (dir != null) {
        val gitDir = File(dir, ".git")
        if (gitDir.exists() && gitDir.isDirectory) {
            return true
        }
        dir = dir.parentFile
    }
    return false
}
