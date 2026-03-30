package com.rk.settings.extension

import android.graphics.Typeface
import android.text.Spanned
import android.widget.TextView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

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
fun MarkdownViewer(url: String, refreshKey: Int, onLoaded: () -> Unit, modifier: Modifier = Modifier) {
    var state by remember(url) { mutableStateOf<MarkdownStatus>(MarkdownStatus.Loading) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val client = remember { OkHttpClient() }

    LaunchedEffect(url, refreshKey) {
        state = MarkdownStatus.Loading
        state = loadMarkdown(url, primaryColor.toArgb(), client)
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
                AndroidView(
                    factory = { ctx -> TextView(ctx) },
                    update = { it.text = state.spanned },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private suspend fun loadMarkdown(url: String, primaryColor: Int, client: OkHttpClient): MarkdownStatus {
    return withContext(Dispatchers.IO) {
        runCatching {
                val markdown =
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val request = Request.Builder().url(url).build()
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
                        markdown,
                        boldColor = primaryColor,
                        inlineCodeColor = primaryColor,
                        codeTypeface = Typeface.MONOSPACE,
                        linkColor = primaryColor,
                    )

                MarkdownStatus.Success(spanned)
            }
            .getOrElse {
                it.printStackTrace()
                MarkdownStatus.Error.Network
            }
    }
}
