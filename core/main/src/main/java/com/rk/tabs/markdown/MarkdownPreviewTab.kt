package com.rk.tabs.markdown

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.vector.ImageVector
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.tabs.base.Tab
import com.rk.utils.isDarkTheme
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app, offline Markdown preview styled to look like GitHub.
 *
 * The Markdown is converted to HTML by [MarkdownToHtml] and wrapped in [GithubMarkdownStyle]'s
 * GitHub-flavored stylesheet, then shown in a WebView with **JavaScript disabled** (so untrusted
 * Markdown can't run code) and no network access for rendering. Relative images/links resolve
 * against the file's own directory. This renders headings, tables, fenced code blocks, blockquotes,
 * task lists, etc. far more faithfully than a plain TextView could.
 */
class MarkdownPreviewTab(override val file: FileObject) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())

    override val name: String
        get() = "Markdown preview"

    override val icon: ImageVector
        get() = XedIcons.Menu_book

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val dark = isDarkTheme(context)

        var html by remember(file, refreshKey) { mutableStateOf<String?>(null) }
        var failed by remember(file, refreshKey) { mutableStateOf(false) }

        LaunchedEffect(file, refreshKey, dark) {
            html = null
            failed = false
            val built =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val text = file.readText() ?: ""
                            GithubMarkdownStyle.document(MarkdownToHtml.convert(text), dark)
                        }
                        .getOrNull()
                }
            if (built == null) failed = true else html = built
        }

        // Base URL so relative images/links resolve against the document's folder.
        val baseUrl =
            (file as? FileWrapper)?.let { File(it.getAbsolutePath()).parentFile?.let { p -> "file://${p.absolutePath}/" } }

        val content = html
        val phase = if (failed) MdPhase.ERROR else if (content == null) MdPhase.LOADING else MdPhase.READY
        Crossfade(targetState = phase, label = "markdown-preview") { p ->
            when (p) {
                MdPhase.LOADING ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                MdPhase.ERROR ->
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Unable to render Markdown", color = MaterialTheme.colorScheme.error)
                    }
                MdPhase.READY ->
                    content?.let { doc ->
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = false
                                    settings.allowFileAccess = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = false
                                    setBackgroundColor(Color.TRANSPARENT)
                                    webViewClient =
                                        object : WebViewClient() {
                                            override fun shouldOverrideUrlLoading(
                                                view: WebView?,
                                                request: WebResourceRequest?,
                                            ): Boolean {
                                                val url = request?.url ?: return false
                                                val scheme = url.scheme?.lowercase()
                                                return if (scheme == "http" || scheme == "https" || scheme == "mailto") {
                                                    runCatching {
                                                        ctx.startActivity(
                                                            Intent(Intent.ACTION_VIEW, url)
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                        }
                                }
                            },
                            update = { web -> web.loadDataWithBaseURL(baseUrl, doc, "text/html", "utf-8", null) },
                        )
                    }
            }
        }
    }
}

private enum class MdPhase {
    LOADING,
    ERROR,
    READY,
}
