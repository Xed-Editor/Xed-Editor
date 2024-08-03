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

import android.text.GetChars;

import androidx.annotation.NonNull;

import java.nio.CharBuffer;

/**
 * Wrapper for char array. Make char array work as a char sequence.
 *
 * @author Rosemoe
 */
public class CharArrayWrapper implements CharSequence, GetChars {

    private final char[] data;
    private final int offset;
    private int count;

    public CharArrayWrapper(@NonNull char[] array, int dataCount) {
        this(array, 0, dataCount);
    }

    public CharArrayWrapper(@NonNull char[] array, int startOffset, int dataCount) {
        data = array;
        count = dataCount;
        offset = startOffset;
    }

    public void setDataCount(int count) {
        this.count = count;
    }

    @Override
    public int length() {
        return count;
    }

    @Override
    public char charAt(int index) {
        return data[offset + index];
    }

    @NonNull
    @Override
    public CharSequence subSequence(int start, int end) {
        return CharBuffer.wrap(data, offset + start, end - start);
    }

    @Override
    public void getChars(int start, int end, char[] dest, int destOffset) {
        if (end > count) {
            throw new StringIndexOutOfBoundsException();
        }
        System.arraycopy(data, start + this.offset, dest, destOffset, end - start);
    }
}
