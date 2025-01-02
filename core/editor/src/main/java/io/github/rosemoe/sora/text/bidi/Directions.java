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
package io.github.rosemoe.sora.text.bidi;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.util.IntPair;

/**
 * Manages directions in a text segment
 *
 * @author Rosemoe
 */
public class Directions {

    private long[] runs;
    private int length;

    public Directions(@NonNull long[] runs, int length) {
        this.runs = runs;
        this.length = length;
    }

    public void setData(@NonNull long[] runs, int length) {
        this.runs = runs;
        this.length = length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getLength() {
        return length;
    }

    public int getRunCount() {
        return runs.length;
    }

    public int getRunStart(int i) {
        return IntPair.getFirst(runs[i]);
    }

    public int getRunEnd(int i) {
        return i == runs.length - 1 ? length : getRunStart(i + 1);
    }

    public boolean isRunRtl(int i) {
        return IntPair.getSecond(runs[i]) == 1;
    }

}
