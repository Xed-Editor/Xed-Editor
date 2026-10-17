package com.rk.editor

import android.graphics.drawable.Drawable
import android.widget.ImageButton
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Represents an extra action button in the editor's text selection window.
 *
 * @see XedTextActionWindow
 */
class TextActionItem(
    val title: String,
    val icon: Drawable,
    val shouldShow: (editor: CodeEditor) -> Boolean = { true },
    val onClick: (editor: CodeEditor) -> Unit,
) {
    var actionButton: ImageButton? = null
}
