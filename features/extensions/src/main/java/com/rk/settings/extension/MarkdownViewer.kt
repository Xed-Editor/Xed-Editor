package com.rk.settings.extension

import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.components.StateScreen
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.utils.logError
import com.rk.utils.okHttpClient
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

sealed interface MarkdownStatus {
    object Loading : MarkdownStatus

    sealed class Error(val stringRes: Int, val drawableRes: Int) : MarkdownStatus {
        object Network : Error(strings.network_err, drawables.cloud_off)

        object Unknown : Error(strings.unknown_err, drawables.error)

        object Empty : Error(strings.empty_err, drawables.file)
    }

    data class Success(val spanned: Spanned) : MarkdownStatus
}

@Composable
fun MarkdownViewer(url: String?, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(url) { mutableStateOf<MarkdownStatus>(MarkdownStatus.Loading) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val client = remember { okHttpClient }

    LaunchedEffect(url, refreshKey) {
        state = MarkdownStatus.Loading
        val forceRefresh = refreshKey > 0
        state = loadMarkdown(url, primaryColor.toArgb(), client, forceRefresh)
        onLoaded()
    }
    AnimatedContent(targetState = state, modifier = modifier.fillMaxWidth()) { state ->
        when (state) {
            MarkdownStatus.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is MarkdownStatus.Error -> {
                val color =
                    when (state) {
                        is MarkdownStatus.Error.Empty -> LocalContentColor.current
                        else -> MaterialTheme.colorScheme.error
                    }
                StateScreen(
                    painter = painterResource(state.drawableRes),
                    text = stringResource(state.stringRes),
                    color = color,
                )
            }

            is MarkdownStatus.Success -> {
                val selectionColors = LocalTextSelectionColors.current
                val selectionBackground = selectionColors.backgroundColor
                AndroidView(
                    factory = { ctx -> TextView(ctx) },
                    update = {
                        it.text = state.spanned
                        it.setTextIsSelectable(true)
                        it.movementMethod = LinkMovementMethod.getInstance()
                        it.highlightColor = selectionBackground.toArgb()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private val protectedCodeRegex = Regex("(?s)(```.*?```|~~~.*?~~~|`[^`]*`|<code>.*?</code>)")
private val unsupportedHtmlRegex =
    Regex("(?is)<(?!/?(?:br|h[1-6]|blockquote|strong|em|code|pre|li|a|ul|ol|p)\\b)[^>]*>")

internal fun removeUnsupportedHtmlTags(markdown: String): String {
    val result = StringBuilder()
    var lastIndex = 0

    protectedCodeRegex.findAll(markdown).forEach { match ->
        val before = markdown.substring(lastIndex, match.range.first)
        result.append(before.replace(unsupportedHtmlRegex, ""))

        result.append(match.value)

        lastIndex = match.range.last + 1
    }

    if (lastIndex < markdown.length) {
        result.append(markdown.substring(lastIndex).replace(unsupportedHtmlRegex, ""))
    }

    return result.toString()
}

private suspend fun loadMarkdown(
    url: String?,
    primaryColor: Int,
    client: OkHttpClient,
    forceRefresh: Boolean = false,
): MarkdownStatus {
    return withContext(Dispatchers.IO) {
        if (url == null) {
            return@withContext MarkdownStatus.Error.Empty
        }

        runCatching {
            val markdown =
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    val requestBuilder = Request.Builder().url(url)
                    if (forceRefresh) {
                        requestBuilder.cacheControl(CacheControl.FORCE_NETWORK)
                    }
                    val request = requestBuilder.build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            return@withContext when (response.code) {
                                404 -> MarkdownStatus.Error.Empty
                                else -> MarkdownStatus.Error.Unknown
                            }
                        }
                        response.body.string()
                    }
                } else {
                    val file = File(url)
                    if (!file.exists()) {
                        return@withContext MarkdownStatus.Error.Empty
                    }
                    file.readText()
                }

            val spanned =
                SimpleMarkdownRenderer.renderAsync(
                    removeUnsupportedHtmlTags(markdown),
                    boldColor = primaryColor,
                    inlineCodeColor = primaryColor,
                    codeTypeface = Typeface.MONOSPACE,
                    linkColor = primaryColor,
                )

            MarkdownStatus.Success(spanned)
        }
            .getOrElse {
                logError(it)
                MarkdownStatus.Error.Network
            }
    }
}
