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

package io.github.rosemoe.sora.util

import io.github.rosemoe.sora.text.CharPosition
import io.github.rosemoe.sora.text.Content
import io.github.rosemoe.sora.text.ICUUtils
import io.github.rosemoe.sora.text.TextRange

/**
 * Utility class for working with characters and indexes.
 *
 * @author Akash Yadav
 */
object Chars {

    /**
     * Find the previous word and get its start position.
     */
    @JvmStatic
    fun prevWordStart(position: CharPosition, text: Content): CharPosition {
        return findWord(position, text, true).start
    }

    /**
     * Find the next word and get its end position.
     */
    @JvmStatic
    fun nextWordEnd(position: CharPosition, text: Content): CharPosition {
        return findWord(position, text).end
    }

    /**
     * Find the previous/next word from the given [character position][position] in the given [text].
     *
     * @param reverse Whether to search for word in reverse or not.
     */
    @JvmStatic
    @JvmOverloads
    fun findWord(position: CharPosition, text: Content, reverse: Boolean = false): TextRange {
        if (reverse) {
            position.column -= 1
        }
        if (position.column <= 0 && position.line > 0 && reverse) {
            val l = position.line - 1
            val pos = CharPosition(l, text.getLine(l).length)
            return TextRange(pos, pos)
        }

        if (text.getColumnCount(position.line) == position.column && position.line < text.lineCount - 1 && !reverse) {
            val pos = CharPosition(position.line + 1, 0)
            return TextRange(pos, pos)
        }

        val column = skipWs(text.getLine(position.line), position.column, reverse)
        return getWordRange(text, position.line, column, false)
    }

    /**
     * Get the range of the word at given character position.
     *
     * @param line   The line.
     * @param column The column.
     * @param useIcu Whether to use the ICU library to get word edges.
     * @return The word range.
     */
    @JvmStatic
    fun getWordRange(text: Content, line: Int, column: Int, useIcu: Boolean): TextRange {
        // Find word edges
        var startLine = line
        var endLine = line
        val lineObj = text.getLine(line)
        val edges = ICUUtils.getWordRange(lineObj, column, useIcu)
        val startOffset = IntPair.getFirst(edges)
        val endOffset = IntPair.getSecond(edges)
        var startColumn = startOffset
        var endColumn = endOffset
        if (startColumn == endColumn) {
            if (endColumn < lineObj.length) {
                endColumn++
            } else if (startColumn > 0) {
                startColumn--
            } else {
                if (line > 0) {
                    val lastColumn = text.getColumnCount(line - 1)
                    startLine = line - 1
                    startColumn = lastColumn
                } else if (line < text.lineCount - 1) {
                    endLine = line + 1
                    endColumn = 0
                }
            }
        }
        return TextRange(
            CharPosition(startLine, startColumn, startOffset),
            CharPosition(endLine, endColumn, endOffset)
        )
    }

    /**
     * Find the next/previous offset after/before [offset] skipping all the whitespaces.
     *
     * @param text The text.
     * @param offset The offset to start from.
     * @param reverse Whether to skip whitespaces towards index 0 or `text.length`.
     */
    @JvmStatic
    @JvmOverloads
    fun skipWs(text: CharSequence, offset: Int, reverse: Boolean = false): Int {
        var i = offset
        while (true) {
            if ((reverse && i < 0) || (!reverse && i == text.length)) {
                break
            }

            val c = text[i]
            if (!c.isWhitespace() || (i == 0 && reverse)) break
            else {
                i += if (reverse) -1 else 1
            }
        }
        return i
    }
}