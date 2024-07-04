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

import io.github.rosemoe.sora.util.ObjectPool;

public class UnicodeIterator {

    private final static ObjectPool<UnicodeIterator> sPool = new ObjectPool<>() {
        @Override
        protected UnicodeIterator allocateNew() {
            return new UnicodeIterator();
        }

        @Override
        protected void onRecycleObject(UnicodeIterator recycledObj) {
            recycledObj.text = null;
        }
    };

    private CharSequence text;
    private int codePoint;
    private int start;
    private int end;
    private int limit;

    private UnicodeIterator() {

    }

    public static UnicodeIterator obtain(@NonNull CharSequence text, int start, int end) {
        var r = sPool.obtain();
        r.set(text, start, end);
        return r;
    }

    public void recycle() {
        sPool.recycle(this);
    }

    public void set(@NonNull CharSequence text, int start, int end) {
        if ((start | end | (end - start) | (text.length() - end)) < 0) {
            throw new IndexOutOfBoundsException();
        }
        this.text = text;
        this.start = this.end = start;
        limit = end;
    }

    public boolean hasNext() {
        return end < limit;
    }

    public int nextCodePoint() {
        start = end;
        if (start >= limit) {
            codePoint = 0;
        } else {
            end++;
            var ch = text.charAt(start);
            if (Character.isHighSurrogate(ch) && end < limit) {
                codePoint = Character.toCodePoint(ch, text.charAt(end));
                end++;
            } else {
                codePoint = ch;
            }
        }
        return codePoint;
    }

    public int getCodePoint() {
        return codePoint;
    }

    public int getStartIndex() {
        return start;
    }

    public int getEndIndex() {
        return end;
    }

}