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

/** The content is passed verbatim: the renderer builds a Compose `AnnotatedString`, so tags cannot execute. */
@Composable
fun AiMarkdownText(markdown: String, modifier: Modifier = Modifier) {
    MarkdownText(
        content = markdown,
        modifier = modifier,
        baseTextStyle = MaterialTheme.typography.bodyMedium,
        imageTransformer = AiMarkdownImageTransformer,
    )
}

/** Decodes inline base64 `data:` URIs, which Coil cannot fetch; other URLs go to the app's transformer. */
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
