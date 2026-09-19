package com.rk.ai.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.rk.lsp.MarkdownDataUriDecoder
import com.rk.markdown.AppMarkdownImageTransformer
import com.rk.markdown.MarkdownText

/**
 * Renders an assistant chat message as Markdown.
 *
 * Replaces the previous sora-editor based renderer, which rasterized the markdown into an Android
 * `TextView` `Spanned`. The Compose renderer supports the full GFM feature set (tables, task lists,
 * alerts, ...) and syntax highlights fenced code blocks.
 *
 * The message is rendered through [MarkdownText] while it is still streaming: that composable parses
 * on `Dispatchers.Default` and keeps the previous result visible until the next parse completes
 * (`retainState = true`), so markdown appears progressively without blocking the main thread.
 *
 * The content is passed to the renderer verbatim. No HTML sanitizing is needed: the renderer only
 * builds a Compose `AnnotatedString`, so tags cannot execute anything.
 */
@Composable
fun AiMarkdownText(markdown: String, modifier: Modifier = Modifier) {
    MarkdownText(
        content = markdown,
        modifier = modifier,
        baseTextStyle = MaterialTheme.typography.bodyMedium,
        imageTransformer = AiMarkdownImageTransformer,
    )
}

/**
 * Image transformer for assistant messages.
 *
 * Some providers inline images directly in the response as base64 `data:` URIs, which Coil cannot fetch
 * by itself. Those are decoded here; every other URL (http/https/file) is delegated to the app's default
 * Coil based transformer.
 */
private object AiMarkdownImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        if (!link.startsWith("data:")) return AppMarkdownImageTransformer.transform(link)

        val bitmap = MarkdownDataUriDecoder.decodeDataUri(link) ?: return null
        return ImageData(
            painter = BitmapPainter(bitmap.asImageBitmap()),
            contentScale = ContentScale.Fit,
            contentDescription = null,
        )
    }

    @Composable
    override fun intrinsicSize(painter: Painter): Size = AppMarkdownImageTransformer.intrinsicSize(painter)
}
