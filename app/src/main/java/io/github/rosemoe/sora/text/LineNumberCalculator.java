/*
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
 */
package io.github.rosemoe.sora.text;

import androidx.annotation.NonNull;

/**
 * A line number calculator for spanner
 *
 * @author Rose
 */
public class LineNumberCalculator {

    private final CharSequence target;
    private final int length;
    private int offset;
    private int line;
    private int column;

    /**
     * Create a new helper for the given text and set offset to start
     *
     * @param target Target text
     */
    public LineNumberCalculator(@NonNull CharSequence target) {
        this.target = target;
        offset = line = column = 0;
        length = this.target.length();
    }

    /**
     * Update line and column for the given advance
     *
     * @param length Advance
     */
    public void update(int length) {
        for (int i = 0; i < length; i++) {
            if (offset + i == this.length) {
                break;
            }
            if (target.charAt(offset + i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        offset = offset + length;
    }

    /**
     * Get line start index
     *
     * @return line start index
     */
    public int findLineStart() {
        return offset - column;
    }

    /**
     * Get line end index
     *
     * @return line end index
     */
    public int findLineEnd() {
        int i = 0;
        for (; i + offset < length; i++) {
            if (target.charAt(offset + i) == '\n') {
                break;
            }
        }
        return offset + i;
    }

    /**
     * Get current line position
     *
     * @return line
     */
    public int getLine() {
        return line;
    }

    /**
     * Get current column position
     *
     * @return column
     */
    public int getColumn() {
        return column;
    }

}

