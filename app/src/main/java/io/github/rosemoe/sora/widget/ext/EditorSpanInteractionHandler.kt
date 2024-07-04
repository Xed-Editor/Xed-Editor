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

package io.github.rosemoe.sora.widget.ext

import android.content.Intent
import android.net.Uri
import io.github.rosemoe.sora.event.ClickEvent
import io.github.rosemoe.sora.event.DoubleClickEvent
import io.github.rosemoe.sora.event.EditorMotionEvent
import io.github.rosemoe.sora.event.LongPressEvent
import io.github.rosemoe.sora.event.subscribeAlways
import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.span.SpanClickableUrl
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.lang.styling.span.SpanInteractionInfo
import io.github.rosemoe.sora.text.TextRange
import io.github.rosemoe.sora.util.IntPair
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.IN_BOUND
import io.github.rosemoe.sora.widget.REGION_TEXT
import io.github.rosemoe.sora.widget.resolveTouchRegion

/**
 * Handle span interaction for editor. This is a optional part of editor currently.
 * If you need to handle span interaction,
 * create this handler with the target editor.
 *
 * Note that do not create multiple handler of the same type for editor.
 * Otherwise, single span interaction event will
 *  be handled multiple times.
 *
 * @author Rosemoe
 */
open class EditorSpanInteractionHandler(val editor: CodeEditor) {

    val eventManager = editor.createSubEventManager()

    init {
        eventManager.subscribeAlways<ClickEvent> { event ->
            if (!event.isFromMouse || (event.isFromMouse && editor.keyMetaStates.isCtrlPressed)) {
                handleInteractionEvent(
                    event,
                    SpanInteractionInfo::isClickable,
                    ::handleSpanClick,
                    !event.isFromMouse
                )
            }
        }
        eventManager.subscribeAlways<DoubleClickEvent> { event ->
            handleInteractionEvent(
                event,
                SpanInteractionInfo::isDoubleClickable,
                ::handleSpanDoubleClick,
                !event.isFromMouse
            )
        }
        eventManager.subscribeAlways<LongPressEvent> { event ->
            handleInteractionEvent(
                event,
                SpanInteractionInfo::isLongClickable,
                ::handleSpanLongClick,
                !event.isFromMouse
            )
        }
    }

    private fun handleInteractionEvent(
        event: EditorMotionEvent,
        predicate: (interactionInfo: SpanInteractionInfo) -> Boolean,
        handler: (Span, SpanInteractionInfo, TextRange) -> Boolean,
        checkCursorRange: Boolean = true
    ) {
        val regionInfo = editor.resolveTouchRegion(event.causingEvent)
        val span = event.span
        val spanRange = event.spanRange
        if (IntPair.getFirst(regionInfo) == REGION_TEXT &&
            IntPair.getSecond(regionInfo) == IN_BOUND &&
            span != null && spanRange != null
        ) {
            if (!checkCursorRange || spanRange.isPositionInside(editor.cursor.left())) {
                span.getSpanExt<SpanInteractionInfo>(SpanExtAttrs.EXT_INTERACTION_INFO)?.let {
                    if (predicate(it)) {
                        if (handler(span, it, spanRange)) {
                            event.intercept()
                        }
                    }
                }
            }
        }
    }

    open fun handleSpanClick(
        span: Span,
        interactionInfo: SpanInteractionInfo,
        spanRange: TextRange
    ): Boolean {
        return false
    }

    open fun handleSpanDoubleClick(
        span: Span,
        interactionInfo: SpanInteractionInfo,
        spanRange: TextRange
    ): Boolean {
        when (interactionInfo) {
            is SpanClickableUrl -> {
                val uri = interactionInfo.getData()
                runCatching {
                    Uri.parse(uri)
                }.onSuccess {
                    val intent = Intent(Intent.ACTION_VIEW, it)
                    editor.context.startActivity(intent)
                }
                return true
            }
        }
        return false
    }

    open fun handleSpanLongClick(
        span: Span,
        interactionInfo: SpanInteractionInfo,
        spanRange: TextRange
    ): Boolean {
        return false
    }

    fun isEnabled() = eventManager.isEnabled

    fun setEnabled(enabled: Boolean) {
        eventManager.isEnabled = enabled
    }


}