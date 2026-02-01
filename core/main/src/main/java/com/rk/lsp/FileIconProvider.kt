package com.rk.lsp

import android.graphics.drawable.Drawable
import com.rk.activities.main.MainActivity
import com.rk.filetree.getDrawableFileIcon
import io.github.rosemoe.sora.lang.completion.SimpleCompletionIconDrawer

/**
 * A custom [io.github.rosemoe.sora.lang.completion.FileIconProvider] that loads file icons from the currently selected
 * icon pack.
 *
 * This provider determines the appropriate icon based on the file name. It retrieves the icon from the
 * `currentIconPack` and renders it as a drawable.
 *
 * @see com.rk.icons.pack.IconPack.getIconFileForName
 */
class FileIconProvider() : io.github.rosemoe.sora.lang.completion.FileIconProvider {
    companion object {
        fun register() {
            SimpleCompletionIconDrawer.globalFileIconProvider = FileIconProvider()
        }
    }

    /**
     * Attempts to load a file/folder icon from the given source string.
     *
     * @param src Source string (e.g., absolute or relative path)
     * @param isFolder True if the source is a folder, false if it's a file
     * @return A [Drawable] if successful, or null if no icon can be loaded.
     */
    override fun load(src: String, isFolder: Boolean): Drawable? {
        val context = MainActivity.instance ?: return null
        return getDrawableFileIcon(context, src.substringAfterLast('/'), isFolder)
    }
}
