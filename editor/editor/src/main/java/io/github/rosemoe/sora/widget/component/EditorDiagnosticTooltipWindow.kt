/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 ******************************************************************************/

package io.github.rosemoe.sora.widget.component

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.PopupMenu
import android.widget.TextView
import io.github.rosemoe.sora.I18nConfig
import io.github.rosemoe.sora.R
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent
import io.github.rosemoe.sora.event.EditorFocusChangeEvent
import io.github.rosemoe.sora.event.EditorReleaseEvent
import io.github.rosemoe.sora.event.HoverEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.event.subscribeAlways
import io.github.rosemoe.sora.event.subscribeEvent
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticDetail
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.util.ViewUtils
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.getComponent
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlin.math.abs

open class EditorDiagnosticTooltipWindow(editor: CodeEditor) : EditorPopupWindow(editor, FEATURE_HIDE_WHEN_FAST_SCROLL or FEATURE_SHOW_OUTSIDE_VIEW_ALLOWED), EditorBuiltinComponent {

    protected val eventManager = editor.createSubEventManager()
    private val rootView: View = LayoutInflater.from(editor.context).inflate(R.layout.diagnostic_tooltip_window, null)
    private val briefMessageText = rootView.findViewById<TextView>(R.id.diagnostic_tooltip_brief_message)
    private val detailMessageText = rootView.findViewById<TextView>(R.id.diagnostic_tooltip_detailed_message)
    private val quickfixText = rootView.findViewById<TextView>(R.id.diagnostic_tooltip_preferred_action)
    private val moreActionText = rootView.findViewById<TextView>(R.id.diagnostic_tooltip_more_actions)
    private val messagePanel = rootView.findViewById<ViewGroup>(R.id.diagnostic_container_message)
    private val quickfixPanel = rootView.findViewById<ViewGroup>(R.id.diagnostic_container_quickfix)
    protected var memorizedPosition: CharPosition? = null
    protected var currentDiagnostic: DiagnosticDetail? = null
        private set
    protected var maxHeight = (editor.dpUnit * 175).toInt()
    private val diagnosticList = mutableListOf<DiagnosticRegion>()
    private val buffer = FloatArray(2)
    private val locationBuffer = IntArray(2)
    protected val popupMenu = PopupMenu(editor.context, moreActionText)
    private var hoverPosition: CharPosition? = null
    private var lastHoverPos = 0f to 0f
    private var menuShown = false
    private var popupHovered = false

    override fun isEnabled() = eventManager.isEnabled

    override fun setEnabled(enabled: Boolean) {
        eventManager.isEnabled = enabled
        if (!enabled) {
            dismiss()
        }
    }

    init {
        super.setContentView(rootView)
        popup.animationStyle = R.style.diagnostic_popup_animation
        rootView.clipToOutline = true
        rootView.setOnGenericMotionListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> popupHovered = true
                MotionEvent.ACTION_HOVER_EXIT -> popupHovered = false
            }
            false
        }
        registerEditorEvents()
        popup.setOnDismissListener {
            currentDiagnostic = null
            popupHovered = false
            menuShown = false
        }
        quickfixText.setOnClickListener {
            val quickfixes = currentDiagnostic?.quickfixes
            if (!quickfixes.isNullOrEmpty()) {
                quickfixes[0].executeQuickfix()
                dismiss()
            }
        }
        moreActionText.setText(I18nConfig.getResourceId(R.string.sora_editor_diagnostics_more_actions))
        moreActionText.setOnClickListener { _ ->
            val quickfixes = currentDiagnostic?.quickfixes
            if (!quickfixes.isNullOrEmpty() && quickfixes.size > 1) {
                popupMenu.menu.apply {
                    clear()
                    for (i in 1 until quickfixes.size) {
                        add(0, i, 0, quickfixes[i].resolveTitle(editor.context))
                    }
                }
                popupMenu.setOnMenuItemClickListener {
                    quickfixes[it.itemId].executeQuickfix()
                    dismiss()
                    true
                }
                popupMenu.show()
                menuShown = true
            }
        }
        popupMenu.setOnDismissListener { menuShown = false }
        applyColorScheme()
    }

    private fun registerEditorEvents() {
        eventManager.subscribeEvent<SelectionChangeEvent> { event, _ ->
            if (!isEnabled || editor.isInMouseMode) {
                return@subscribeEvent
            }
            if (event.isSelected || (event.cause != SelectionChangeEvent.CAUSE_TAP && event.cause != SelectionChangeEvent.CAUSE_TEXT_MODIFICATION)) {
                updateDiagnostic(null, null)
                return@subscribeEvent
            }
            updateDiagnostic(event.left)
        }
        eventManager.subscribeEvent<ScrollEvent> { _, _ ->
            if (editor.isInMouseMode) {
                return@subscribeEvent
            }
            if (currentDiagnostic != null && isShowing) {
                if (!isSelectionVisible()) {
                    dismiss()
                } else {
                    updateWindowPosition()
                }
            }
        }
        val callback = Runnable {
            val pos = hoverPosition
            if (popup.isShowing) {
                if (!(popupHovered || menuShown)) {
                    pos?.let { updateDiagnostic(it) }
                }
            } else {
                pos?.let { updateDiagnostic(it) }
            }
        }

        fun postUpdate(delay: Long = ViewUtils.HOVER_TOOLTIP_SHOW_TIMEOUT) {
            editor.removeCallbacks(callback)
            editor.postDelayedInLifecycle(callback, delay)
        }
        eventManager.subscribeAlways<HoverEvent> { e ->
            if (editor.isInMouseMode) {
                fun updateLastHover() {
                    lastHoverPos = e.x to e.y
                }
                when (e.causingEvent.action) {
                    MotionEvent.ACTION_HOVER_ENTER -> {
                        editor.removeCallbacks(callback)
                        updateDiagnostic(null, null)
                        updateLastHover()
                    }

                    MotionEvent.ACTION_HOVER_EXIT -> {
                        hoverPosition = null
                        if (!(popupHovered || menuShown)) {
                            postUpdate()
                            updateLastHover()
                        }
                    }

                    MotionEvent.ACTION_HOVER_MOVE -> {
                        if (!(popupHovered || menuShown)) {
                            if (editor.isScreenPointOnText(e.x, e.y)) {
                                if (abs(e.x - lastHoverPos.first) > ViewUtils.HOVER_TAP_SLOP || abs(
                                        e.y - lastHoverPos.second
                                    ) > ViewUtils.HOVER_TAP_SLOP
                                ) {
                                    updateLastHover()
                                    val pos = editor.getPointPositionOnScreen(e.x, e.y)
                                    hoverPosition = editor.text.indexer.getCharPosition(
                                        IntPair.getFirst(pos),
                                        IntPair.getSecond(pos)
                                    )
                                    postUpdate()
                                }
                            } else {
                                hoverPosition = null
                                postUpdate()
                            }
                        }
                    }
                }
            }
        }
        eventManager.subscribeEvent<ColorSchemeUpdateEvent> { _, _ ->
            applyColorScheme()
        }
        eventManager.subscribeEvent<EditorFocusChangeEvent> { event, _ ->
            if (!event.isGainFocus) {
                dismiss()
            }
        }
        eventManager.subscribeEvent<EditorReleaseEvent> { _, _ ->
            isEnabled = false
        }
    }

    override fun dismiss() {
        if (isShowing) {
            Thread.dumpStack()
            super.dismiss()
        }
    }

    protected open fun updateDiagnostic(pos: CharPosition) {
        val diagnostics = editor.diagnostics
        if (diagnostics != null) {
            diagnostics.queryInRegion(
                diagnosticList,
                pos.index - 1,
                pos.index + 1
            )
            if (diagnosticList.isNotEmpty()) {
                var minLength = diagnosticList[0].endIndex - diagnosticList[0].startIndex
                var minIndex = 0
                for (i in 1 until diagnosticList.size) {
                    val length = diagnosticList[i].endIndex - diagnosticList[i].startIndex
                    if (length < minLength) {
                        minLength = length
                        minIndex = i
                    }
                }
                updateDiagnostic(diagnosticList[minIndex].detail, pos)
                if (!editor.getComponent<EditorAutoCompletion>().isCompletionInProgress)
                    show()
            } else {
                updateDiagnostic(null, null)
            }
            diagnosticList.clear()
        } else {
            updateDiagnostic(null, null)
        }
    }

    protected open fun applyColorScheme() {
        val colorScheme = editor.colorScheme
        briefMessageText.setTextColor(colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG))
        detailMessageText.setTextColor(colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG))
        quickfixText.setTextColor(colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION))
        moreActionText.setTextColor(colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION))
        val background = GradientDrawable()
        background.cornerRadius = editor.dpUnit * 5
        background.setColor(colorScheme.getColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND))
        rootView.background = background
    }

    protected open fun isSelectionVisible(): Boolean {
        val selection = editor.cursor.left()
        editor.layout.getCharLayoutOffset(selection.line, selection.column, buffer)
        return buffer[0] >= editor.offsetY && buffer[0] - editor.rowHeight <= editor.offsetY + editor.height && buffer[1] >= editor.offsetX && buffer[1] - 100f /* larger than a single character */ <= editor.offsetX + editor.width
    }

    protected open fun updateDiagnostic(diagnostic: DiagnosticDetail?, position: CharPosition?) {
        if (!isEnabled) {
            return
        }
        if (diagnostic == currentDiagnostic) {
            if (diagnostic != null && !editor.isInMouseMode) {
                updateWindowPosition()
            }
            return
        }
        currentDiagnostic = diagnostic
        memorizedPosition = position
        if (diagnostic == null) {
            dismiss()
            return
        }
        briefMessageText.text = diagnostic.briefMessage.ifBlank { "<NULL>" }
        val detailedMessage = diagnostic.detailedMessage
        if (detailedMessage != null) {
            detailMessageText.text = detailedMessage
            detailMessageText.visibility = View.VISIBLE
        } else {
            detailMessageText.visibility = View.GONE
        }
        val quickfixes = diagnostic.quickfixes
        if (quickfixes.isNullOrEmpty()) {
            quickfixPanel.visibility = View.GONE
        } else {
            quickfixPanel.visibility = View.VISIBLE
            quickfixText.text = quickfixes[0].resolveTitle(editor.context)
            moreActionText.visibility = if (quickfixes.size > 1) View.VISIBLE else View.GONE
        }
        updateWindowSize()
        updateWindowPosition()
    }

    protected open fun updateWindowSize() {
        val width = (editor.width * 0.9).toInt()
        // First, measure the bottom bar
        var bottomBarHeight = 0
        var bottomBarWidth = 0
        if (quickfixPanel.visibility == View.VISIBLE) {
            quickfixPanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(114514, MeasureSpec.AT_MOST))
            bottomBarHeight = quickfixPanel.measuredHeight
            bottomBarWidth = quickfixPanel.measuredWidth.coerceAtMost(width)
        }
        // Then, measure the message region
        val restHeight = (maxHeight - bottomBarHeight).coerceAtLeast(1)
        messagePanel.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
        messagePanel.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(restHeight, MeasureSpec.AT_MOST))
        val messageHeight = messagePanel.measuredHeight.coerceAtMost(restHeight)
        val messageWidth = messagePanel.measuredWidth.coerceAtMost(width)
        messagePanel.layoutParams.height = messageHeight
        val dialogWidth = Math.max(bottomBarWidth, messageWidth)
        val dialogHeight = bottomBarHeight + messageHeight
        setSize(dialogWidth, dialogHeight)
    }

    protected open fun updateWindowPosition() {
        val selection = memorizedPosition ?: return
        val charX = editor.getCharOffsetX(selection.line, selection.column)
        val charY = editor.getCharOffsetY(selection.line, selection.column) - editor.rowHeight
        editor.getLocationInWindow(locationBuffer)
        val restAbove = charY + locationBuffer[1]
        val restBottom = editor.height - charY - editor.rowHeight
        val completionShowing = editor.getComponent<EditorAutoCompletion>().isShowing
        val windowY = if (restAbove > restBottom || completionShowing) {
            charY - height
        } else {
            charY + editor.rowHeight * 1.5f
        }
        if (completionShowing && windowY < 0) {
            dismiss()
            return
        }
        val windowX = (charX - width / 2).coerceAtLeast(0f)
        setLocationAbsolutely(windowX.toInt(), windowY.toInt())
    }

}