package com.rk.editor

import android.widget.ImageButton
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Represents an extra action button in the editor's text selection window.
 *
 * @see XedTextActionWindow
 */
class TextActionItem(
    @field:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val shouldShow: (editor: CodeEditor) -> Boolean = { true },
    val onClick: (editor: CodeEditor) -> Unit,
) {
    var actionButton: ImageButton? = null
}
