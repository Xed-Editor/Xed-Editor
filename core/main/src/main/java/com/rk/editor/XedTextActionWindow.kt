package com.rk.editor

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

data class XedTextActionItem(
    val titleRes: Int,
    val iconRes: Int,
    val shouldShow: (editor: CodeEditor) -> Boolean,
    val onClick: (editor: CodeEditor) -> Unit,
)

class XedTextActionWindow(private val codeEditor: CodeEditor) : EditorTextActionWindow(codeEditor) {

    private val customActions = mutableListOf<XedTextActionItem>()
    private val actionButtons = mutableListOf<ImageButton>()
    private var customContainer: LinearLayout? = null

    fun registerAction(item: XedTextActionItem) {
        customActions.add(item)
        val button = createActionButton(item)
        actionButtons.add(button)
        customContainer?.addView(button)
    }

    fun unregisterAction(item: XedTextActionItem) {
        val idx = customActions.indexOf(item)
        if (idx >= 0) {
            customActions.removeAt(idx)
            val btn = actionButtons.removeAt(idx)
            (btn.parent as? ViewGroup)?.removeView(btn)
        }
    }

    private fun ensureCustomContainer() {
        if (customContainer == null) {
            val parent = getView() as? ViewGroup ?: return
            customContainer = LinearLayout(codeEditor.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            parent.addView(customContainer)
        }
    }

    private fun createActionButton(item: XedTextActionItem): ImageButton {
        return ImageButton(codeEditor.context).apply {
            setImageResource(item.iconRes)
            contentDescription = context.getString(item.titleRes)
            val padding = (codeEditor.dpUnit * 8).toInt()
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(android.R.color.transparent)
            setOnClickListener {
                item.onClick(codeEditor)
                dismiss()
            }
        }
    }

    override fun displayWindow() {
        ensureCustomContainer()
        updateCustomActions()
        super.displayWindow()
    }

    override fun applyColorScheme() {
        super.applyColorScheme()
        val color = codeEditor.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
        actionButtons.forEach { btn ->
            btn.setColorFilter(PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP))
        }
    }

    private fun updateCustomActions() {
        customActions.forEachIndexed { idx, item ->
            val visible = item.shouldShow(codeEditor)
            actionButtons[idx].visibility = if (visible) View.VISIBLE else View.GONE
        }
    }
}
