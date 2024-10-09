/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2023  Rosemoe
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

package io.github.rosemoe.sora.lsp.editor.format

import io.github.rosemoe.sora.lang.format.AsyncFormatter
import io.github.rosemoe.sora.lsp.editor.LspLanguage
import io.github.rosemoe.sora.lsp.events.EventType
import io.github.rosemoe.sora.lsp.events.format.fullFormatting
import io.github.rosemoe.sora.lsp.events.format.rangeFormatting

import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.TextRange
import kotlinx.coroutines.future.future


class LspFormatter(private var language: LspLanguage?) :
    AsyncFormatter() {

    private val eventManager = requireNotNull(language).editor.eventManager

    private val coroutineScope = requireNotNull(language).editor.coroutineScope

    override fun formatAsync(text: Content, cursorRange: TextRange): TextRange? {

        coroutineScope.future {
            eventManager.emitAsync(EventType.fullFormatting, text)
        }.get()

        return null
    }

    override fun formatRegionAsync(
        text: Content,
        rangeToFormat: TextRange,
        cursorRange: TextRange
    ): TextRange? {

        coroutineScope.future {
            eventManager.emitAsync(EventType.rangeFormatting) {
                put("text", text)
                put("range", rangeToFormat)
            }
        }.get()

        return null
    }

    override fun destroy() {
        super.destroy()
        language = null
    }
}

