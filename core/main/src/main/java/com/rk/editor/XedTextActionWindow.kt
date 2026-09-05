package com.rk.editor

import android.graphics.RectF
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import io.github.rosemoe.sora.R as SoraR
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.min

/**
 * Replaces the default [EditorTextActionWindow] via [CodeEditor.replaceComponent].
 *
 * Shows the built-in text actions plus any extra actions registered through
 * [registerTextAction]. The popup is widened to fit all visible buttons instead
 * of sora's fixed 230dp cap.
 */
class XedTextActionWindow(editor: CodeEditor) : EditorTextActionWindow(editor) {

    private var initialized = false

    init {
        initialized = true
    }

    private val items by lazy { mutableListOf<Pair<TextActionItem, ImageButton>>() }

    private val rootRow: ViewGroup by lazy {
        val root = getView()
        val scroll = root.getChildAt(0) as ViewGroup
        scroll.getChildAt(0) as ViewGroup
    }

    private val selectAllBtn by lazy { rootRow.findViewById<ImageButton>(SoraR.id.panel_btn_select_all) }
    private val copyBtn by lazy { rootRow.findViewById<ImageButton>(SoraR.id.panel_btn_copy) }
    private val pasteBtn by lazy { rootRow.findViewById<ImageButton>(SoraR.id.panel_btn_paste) }
    private val longSelectBtn by lazy { rootRow.findViewById<ImageButton>(SoraR.id.panel_btn_long_select) }
    private val cutBtn by lazy { rootRow.findViewById<ImageButton>(SoraR.id.panel_btn_cut) }

    fun registerTextAction(item: TextActionItem) {
        if (items.any { it.first === item }) return
        val editor = getEditor()
        val btn = ImageButton(editor.context).apply {
            setImageResource(item.iconRes)
            contentDescription = editor.context.getString(item.titleRes)
            val btnSize = (editor.dpUnit * 45).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            val value = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
            setBackgroundResource(value.resourceId)
            setOnClickListener {
                try {
                    item.onClick(editor)
                } catch (t: Throwable) {
                    Log.w(TAG, "Text action failed", t)
                }
                dismiss()
            }
        }
        item.actionButton = btn
        rootRow.addView(btn)
        items += item to btn
        applyColorFilter(btn, editor.colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR))
    }

    fun unregisterTextAction(item: TextActionItem) {
        val entry = items.firstOrNull { it.first === item } ?: return
        rootRow.removeView(entry.second)
        items.remove(entry)
        item.actionButton = null
    }

    override fun applyColorScheme() {
        super.applyColorScheme()
        if (!initialized) return
        val color = getEditor().colorScheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)
        for ((_, btn) in items) {
            applyColorFilter(btn, color)
        }
    }

    override fun displayWindow() {
        val editor = getEditor()
        updateDefaultButtonStates()
        updateExtraButtonStates()

        val width = computeWidth()
        val height = getHeight()
        setSize(width, height)

        val top = computeTop(editor, height)
        val cursor = editor.cursor
        val handleLeftX = editor.getOffset(cursor.leftLine, cursor.leftColumn)
        val handleRightX = editor.getOffset(cursor.rightLine, cursor.rightColumn)
        val centerX = (handleLeftX + handleRightX) / 2f
        val panelX = (centerX - width / 2f).toInt()
            .coerceIn(0, (editor.width - width).coerceAtLeast(0))

        setLocationAbsolutely(panelX, top)
        show()
    }

    private fun updateDefaultButtonStates() {
        val editor = getEditor()
        val selected = editor.cursor.isSelected
        val editable = editor.isEditable
        pasteBtn.isEnabled = editor.hasClip()
        copyBtn.visibility = if (selected) View.VISIBLE else View.GONE
        pasteBtn.visibility = if (editable) View.VISIBLE else View.GONE
        cutBtn.visibility = if (selected && editable) View.VISIBLE else View.GONE
        longSelectBtn.visibility = if (!selected && editable) View.VISIBLE else View.GONE
    }

    private fun updateExtraButtonStates() {
        val editor = getEditor()
        for ((item, btn) in items) {
            btn.visibility = if (item.shouldShow(editor)) View.VISIBLE else View.GONE
        }
    }

    private fun computeWidth(): Int {
        val root = getView()
        root.measure(
            View.MeasureSpec.makeMeasureSpec(1000000, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(100000, View.MeasureSpec.AT_MOST),
        )
        val content = root.measuredWidth
        val maxWidth = (getEditor().width * 0.92f).toInt().coerceAtLeast(1)
        return min(content, maxWidth)
    }

    private fun computeTop(editor: CodeEditor, height: Int): Int {
        val top = if (editor.cursor.isSelected) {
            val top1 = selectTop(editor.leftHandleDescriptor.position, height)
            val top2 = selectTop(editor.rightHandleDescriptor.position, height)
            min(top1, top2)
        } else {
            selectTop(editor.insertHandleDescriptor.position, height)
        }
        return top.coerceIn(0, (editor.height - height - 5).coerceAtLeast(0))
    }

    private fun selectTop(rect: RectF, height: Int): Int {
        val rowHeight = getEditor().rowHeight
        return if (rect.top - rowHeight * 3 / 2F > height) {
            (rect.top - rowHeight * 3 / 2 - height).toInt()
        } else {
            (rect.bottom + rowHeight / 2).toInt()
        }
    }

    private companion object {
        const val TAG = "XedTextActionWindow"
    }
}
