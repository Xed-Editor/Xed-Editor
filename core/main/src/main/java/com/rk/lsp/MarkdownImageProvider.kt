package com.rk.lsp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import com.caverock.androidsvg.SVG
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer

/**
 * A custom image provider implementation for rendering images within Markdown content in the editor.
 *
 * This class specifically handles Base64 encoded strings. It supports loading and rendering:
 * - **SVG Images:** Parsed using AndroidSVG, with automatic scaling to ensure a minimum visibility size.
 * - **Raster Images:** Decoded via `BitmapFactory` (e.g., PNG, JPEG), with automatic downscaling to fit within a
 *   maximum width.
 *
 * Implements [SimpleMarkdownRenderer.ImageProvider] to integrate with the sora-editor's Markdown rendering.
 */
class MarkdownImageProvider : SimpleMarkdownRenderer.ImageProvider {
    companion object {
        fun register() {
            SimpleMarkdownRenderer.globalImageProvider = MarkdownImageProvider()
        }
    }

    /**
     * Attempts to load an image from the given source string.
     *
     * @param src Source string (e.g., data URI, file path, URL)
     * @return A [Drawable] if successful, or null if the image cannot be loaded.
     */
    override fun load(src: String): Drawable? = MarkdownDataUriDecoder.decodeDataUri(src)?.let { BitmapDrawable(it) }
}

/**
 * Decodes an inline `data:` URI into a [Bitmap].
 *
 * This is shared by the sora-editor markdown renderer (used for LSP hover/signature documentation, which
 * expects a [Drawable]) and by the Compose markdown renderer used in the AI chat (which expects a Compose
 * `Painter`).
 *
 * Supports base64 encoded payloads:
 * - **SVG images:** parsed using AndroidSVG, scaled so the result stays within a visible size range.
 * - **Raster images:** decoded via `BitmapFactory`, downscaled to fit within a maximum width.
 */
object MarkdownDataUriDecoder {
    private const val MIN_DIMENSION = 175f
    private const val MAX_DIMENSION = 800f
    private const val MAX_RASTER_WIDTH = 800

    /** @return the decoded bitmap, or `null` when [src] is not a supported `data:` URI. */
    fun decodeDataUri(src: String): Bitmap? {
        if (!src.startsWith("data:")) return null

        val mime = src.substringAfter("data:").substringBefore(";")
        val payload = src.substringAfter("base64,", "")
        if (payload.isEmpty()) return null

        val imageByteArray =
            try {
                Base64.decode(payload, Base64.DEFAULT)
            } catch (_: Exception) {
                return null
            }

        return when (mime) {
            "image/svg+xml" -> decodeSvg(imageByteArray)
            else -> decodeRaster(imageByteArray)
        }
    }

    private fun decodeSvg(imageByteArray: ByteArray): Bitmap? {
        val svg =
            try {
                SVG.getFromString(String(imageByteArray))
            } catch (_: Exception) {
                return null
            }

        val originalWidth = svg.documentWidth
        val originalHeight = svg.documentHeight
        if (originalWidth <= 0f || originalHeight <= 0f) return null

        val clampedWidth = originalWidth.coerceIn(MIN_DIMENSION, MAX_DIMENSION)
        val clampedHeight = originalHeight.coerceIn(MIN_DIMENSION, MAX_DIMENSION)

        val scale = minOf(clampedWidth / originalWidth, clampedHeight / originalHeight)

        val scaledWidth = (originalWidth * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (originalHeight * scale).toInt().coerceAtLeast(1)

        val bitmap = createBitmap(scaledWidth, scaledHeight)
        val canvas = Canvas(bitmap)

        canvas.scale(scale, scale)
        svg.renderToCanvas(canvas)

        return bitmap
    }

    private fun decodeRaster(imageByteArray: ByteArray): Bitmap? {
        val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size) ?: return null
        return scaleIfNeeded(bitmap, MAX_RASTER_WIDTH)
    }

    /**
     * Scale down a bitmap to maxWidth preserving aspect ratio. If bitmap width is already <= maxWidth, the original
     * bitmap is returned.
     */
    private fun scaleIfNeeded(bmp: Bitmap, maxWidth: Int): Bitmap {
        val currentWidth = bmp.width
        if (currentWidth <= maxWidth) return bmp
        val ratio = maxWidth.toFloat() / currentWidth.toFloat()

        val newHeight = (bmp.height * ratio).toInt().coerceAtLeast(1)
        return bmp.scale(maxWidth, newHeight)
    }
}
