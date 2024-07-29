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
 * A range made up of two {@link CharPosition} objects.
 *
 * @author Rosemoe
 */
public class TextRange {

    private CharPosition start;
    private CharPosition end;

    public TextRange(@NonNull CharPosition start, @NonNull CharPosition end) {
        this.start = start;
        this.end = end;
    }

    @NonNull
    public CharPosition getStart() {
        return start;
    }

    public void setStart(@NonNull CharPosition start) {
        this.start = start;
    }

    @NonNull
    public CharPosition getEnd() {
        return end;
    }

    public void setEnd(@NonNull CharPosition end) {
        this.end = end;
    }

    public int getStartIndex() {
        return start.index;
    }

    public int getEndIndex() {
        return end.index;
    }

    /**
     * Check if the given position is inside the range
     */
    public boolean isPositionInside(@NonNull CharPosition pos) {
        return pos.index >= start.index && pos.index < end.index;
    }

    @NonNull
    @Override
    public String toString() {
        return "TextRange{" +
                "start=" + start +
                ", end=" + end +
                '}';
    }
}
