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

import java.util.Objects;

import io.github.rosemoe.sora.util.IntPair;

/**
 * This a data class of a character position in {@link Content}
 *
 * @author Rosemoe
 */
public final class CharPosition {

    public int index;

    public int line;

    public int column;

    public CharPosition() {
    }

    public CharPosition(int line, int column) {
        this(line, column, -1);
    }

    public CharPosition(int line, int column, int index) {
        this.index = index;
        this.line = line;
        this.column = column;
    }

    /**
     * Get the index
     *
     * @return index
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get column
     *
     * @return column
     */
    public int getColumn() {
        return column;
    }

    /**
     * Get line
     *
     * @return line
     */
    public int getLine() {
        return line;
    }

    /**
     * Make this CharPosition zero and return self
     *
     * @return self
     */
    public CharPosition toBOF() {
        index = line = column = 0;
        return this;
    }

    @Override
    public boolean equals(Object another) {
        if (another instanceof CharPosition pos) {
            return pos.column == column &&
                    pos.line == line &&
                    pos.index == index;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, line, column);
    }

    /**
     * Convert {@link CharPosition#line} and {@link CharPosition#column} to a Long number
     * <p>
     * First integer is line and second integer is column
     *
     * @return A Long integer describing the position
     */
    public long toIntPair() {
        return IntPair.pack(line, column);
    }

    /**
     * Make a copy of this CharPosition and return the copy
     *
     * @return New CharPosition including info of this CharPosition
     */
    @NonNull
    public CharPosition fromThis() {
        var pos = new CharPosition();
        pos.set(this);
        return pos;
    }

    /**
     * Set this {@link CharPosition} object's data the same as {@code another}
     */
    public void set(@NonNull CharPosition another) {
        index = another.index;
        line = another.line;
        column = another.column;
    }

    @NonNull
    @Override
    public String toString() {
        return "CharPosition(line = " + line + ",column = " + column + ",index = " + index + ")";
    }

}
