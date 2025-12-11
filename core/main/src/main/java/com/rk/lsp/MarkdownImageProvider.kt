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
    override fun load(src: String): Drawable? {
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
            "image/svg+xml" -> loadSvg(imageByteArray)
            else -> loadRaster(imageByteArray)
        }
    }

    private fun loadSvg(imageByteArray: ByteArray): Drawable? {
        val svgText = String(imageByteArray)
        val svg =
            try {
                SVG.getFromString(svgText)
            } catch (_: Exception) {
                return null
            }

        val originalWidth = svg.documentWidth
        val originalHeight = svg.documentHeight

        val clampedWidth = originalWidth.coerceIn(175f, 800f)
        val clampedHeight = originalHeight.coerceIn(175f, 800f)

        val scaleX = clampedWidth / originalWidth
        val scaleY = clampedHeight / originalHeight
        val scale = minOf(scaleX, scaleY)

        val scaledWidth = (originalWidth * scale).toInt()
        val scaledHeight = (originalHeight * scale).toInt()

        val bitmap = createBitmap(scaledWidth, scaledHeight)
        val canvas = Canvas(bitmap)

        canvas.scale(scale, scale)
        svg.renderToCanvas(canvas)

        return BitmapDrawable(bitmap)
    }

    private fun loadRaster(imageByteArray: ByteArray): Drawable? {
        val bitmap = BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size) ?: return null
        val scaledBitmap = scaleIfNeeded(bitmap, 800)
        return BitmapDrawable(scaledBitmap)
    }

    /**
     * Scale down a bitmap to maxWidth preserving aspect ratio. If bitmap width is already <= maxWidth, the original
     * bitmap is returned.
     */
    private fun scaleIfNeeded(bmp: Bitmap, maxWidth: Int): Bitmap {
        val currentWidth = bmp.width
        if (currentWidth <= maxWidth) return bmp
        val ratio = maxWidth.toFloat() / currentWidth.toFloat()

        val newHeight = (bmp.height * ratio).toInt()
        return bmp.scale(maxWidth, newHeight)
    }
}
