package com.rk.tabs.markdown

import android.graphics.Typeface
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.rk.file.FileObject
import com.rk.icons.Menu_book
import com.rk.icons.XedIcons
import com.rk.tabs.base.Tab
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-app, native Markdown preview. Renders the file with [SimpleMarkdownRenderer] (the same engine
 * used for extension READMEs) into a themed [TextView]. This is instant and offline — no WebView,
 * no CDN, and it inherits the app's Material theme colors.
 */
class MarkdownPreviewTab(override val file: FileObject) : Tab() {

    override var tabTitle: MutableState<String> = mutableStateOf(file.getName())

    override val name: String
        get() = "Markdown preview"

    override val icon: ImageVector
        get() = XedIcons.Menu_book

    @Composable
    override fun Content() {
        val accent = MaterialTheme.colorScheme.primary.toArgb()
        val selectionBg = LocalTextSelectionColors.current.backgroundColor.toArgb()

        var spanned by remember(file, refreshKey) { mutableStateOf<Spanned?>(null) }
        var failed by remember(file, refreshKey) { mutableStateOf(false) }

        LaunchedEffect(file, refreshKey) {
            spanned = null
            failed = false
            val rendered =
                withContext(Dispatchers.IO) {
                    runCatching {
                            val text = file.readText() ?: ""
                            SimpleMarkdownRenderer.renderAsync(
                                text,
                                boldColor = accent,
                                inlineCodeColor = accent,
                                codeTypeface = Typeface.MONOSPACE,
                                linkColor = accent,
                            )
                        }
                        .getOrNull()
                }
            if (rendered == null) failed = true else spanned = rendered
        }

        val content = spanned
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
                    content?.let { rendered ->
                        Column(
                            modifier =
                                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                        ) {
                            AndroidView(
                                factory = { ctx -> TextView(ctx) },
                                update = { tv ->
                                    tv.text = rendered
                                    tv.setTextIsSelectable(true)
                                    tv.movementMethod = LinkMovementMethod.getInstance()
                                    tv.highlightColor = selectionBg
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
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
