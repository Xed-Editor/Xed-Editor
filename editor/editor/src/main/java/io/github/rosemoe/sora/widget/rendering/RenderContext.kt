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

package io.github.rosemoe.sora.widget.rendering

import android.os.Build
import io.github.rosemoe.sora.lang.analysis.StyleUpdateRange
import io.github.rosemoe.sora.widget.CodeEditor

/**
 * Context for editor rendering process. It stores rendering attributes and caches.
 *
 * @author Rosemoe
 */
class RenderContext(val editor: CodeEditor) {

    val cache = RenderCache()

    val renderNodeHolder: RenderNodeHolder? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        RenderNodeHolder(editor)
    } else {
        null
    }

    var tabWidth = 4

    fun updateForRange(range: StyleUpdateRange) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeHolder?.invalidateInRegion(range)
        }
    }

    fun invalidateRenderNodes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeHolder?.invalidate()
        }
    }

    fun updateForInsertion(startLine: Int, endLine: Int) {
        cache.updateForInsertion(startLine, endLine)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeHolder?.afterInsert(startLine, endLine)
        }
    }

    fun updateForDeletion(startLine: Int, endLine: Int) {
        cache.updateForDeletion(startLine, endLine)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            renderNodeHolder?.afterDelete(startLine, endLine)
        }
    }

    fun reset(lineCount: Int) {
        cache.reset(lineCount)
    }

}