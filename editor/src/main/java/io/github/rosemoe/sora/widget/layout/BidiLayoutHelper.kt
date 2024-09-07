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

package io.github.rosemoe.sora.widget.layout

import io.github.rosemoe.sora.graphics.CharPosDesc
import io.github.rosemoe.sora.graphics.GraphicTextRow
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.widget.CodeEditor

object BidiLayoutHelper {

    fun horizontalOffset(
        editor: CodeEditor,
        layout: AbstractLayout,
        text: Content,
        line: Int,
        rowStart: Int,
        rowEnd: Int,
        targetColumn: Int
    ): Float {
        val dirs = text.getLineDirections(line)
        val lineText = text.getLine(line)
        val gtr = GraphicTextRow.obtain(editor.isBasicDisplayMode)
        gtr.set(
            text,
            line,
            0,
            lineText.length,
            layout.getSpans(line),
            editor.textPaint,
            editor.renderContext
        )
        if (layout is WordwrapLayout) {
            gtr.setSoftBreaks(layout.getSoftBreaksForLine(line))
        }
        val column = targetColumn.coerceIn(rowStart, rowEnd)
        var offset = 0f
        for (i in 0 until dirs.runCount) {
            val runStart = dirs.getRunStart(i).coerceIn(rowStart, rowEnd)
            val runEnd = dirs.getRunEnd(i).coerceIn(rowStart, rowEnd)
            if (runStart > column || runStart > runEnd) {
                break
            }
            offset += if (runEnd < column) {
                gtr.measureText(runStart, runEnd)
            } else { //runEnd > targetColumn
                if (dirs.isRunRtl(i)) {
                    gtr.measureText(targetColumn, runEnd)
                } else {
                    gtr.measureText(runStart, column)
                }
            }
        }
        gtr.recycle()
        return offset
    }

    fun horizontalIndex(
        editor: CodeEditor,
        layout: AbstractLayout,
        text: Content,
        line: Int,
        rowStart: Int,
        rowEnd: Int,
        targetOffset: Float
    ): Int {
        val dirs = text.getLineDirections(line)
        val lineText = text.getLine(line)
        val gtr = GraphicTextRow.obtain(editor.isBasicDisplayMode)
        gtr.set(
            text,
            line,
            0,
            lineText.length,
            layout.getSpans(line),
            editor.textPaint,
            editor.renderContext
        )
        if (layout is WordwrapLayout) {
            gtr.setSoftBreaks(layout.getSoftBreaksForLine(line))
        }
        var offset = 0f
        for (i in 0 until dirs.runCount) {
            val runStart = dirs.getRunStart(i).coerceIn(rowStart, rowEnd)
            val runEnd = dirs.getRunEnd(i).coerceIn(rowStart, rowEnd)
            if (runEnd == rowStart) {
                continue
            }
            if (runStart == rowEnd) {
                val j = if (i > 0) i - 1 else 0
                return if (dirs.isRunRtl(j)) {
                    dirs.getRunStart(j).coerceIn(rowStart, rowEnd)
                } else {
                    dirs.getRunEnd(j).coerceIn(rowStart, rowEnd)
                }
            }
            val width = gtr.measureText(runStart, runEnd)
            if (offset + width >= targetOffset) {
                val res = if (dirs.isRunRtl(i)) {
                    CharPosDesc.getTextOffset(
                        gtr.findOffsetByAdvance(
                            runStart,
                            offset + width - targetOffset
                        )
                    )
                } else {
                    CharPosDesc.getTextOffset(
                        gtr.findOffsetByAdvance(
                            runStart,
                            targetOffset - offset
                        )
                    )
                }
                gtr.recycle()
                return res
            } else {
                offset += width
            }
        }
        gtr.recycle()
        // Fallback
        val j = dirs.runCount - 1
        return if (dirs.isRunRtl(j)) {
            dirs.getRunStart(j).coerceIn(rowStart, rowEnd)
        } else {
            dirs.getRunEnd(j).coerceIn(rowStart, rowEnd)
        }
    }

}