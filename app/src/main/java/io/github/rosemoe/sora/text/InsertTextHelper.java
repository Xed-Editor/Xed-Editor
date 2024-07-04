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

class InsertTextHelper {

    private static final InsertTextHelper[] sCached = new InsertTextHelper[8];
    static int TYPE_LINE_CONTENT = 0;
    static int TYPE_NEWLINE = 1;
    static int TYPE_EOF = 2;
    private CharSequence text;
    private int index, indexNext, length;

    private synchronized static InsertTextHelper obtain() {
        for (int i = 0; i < sCached.length; i++) {
            if (sCached[i] != null) {
                var cache = sCached[i];
                sCached[i] = null;
                return cache;
            }
        }
        return new InsertTextHelper();
    }

    public static InsertTextHelper forInsertion(@NonNull CharSequence text) {
        var o = obtain();
        o.init(text);
        return o;
    }

    public void recycle() {
        synchronized (InsertTextHelper.class) {
            for (int i = 0; i < sCached.length; i++) {
                if (sCached[i] == null) {
                    sCached[i] = this;
                    reset();
                    break;
                }
            }
        }
    }

    private void init(@NonNull CharSequence text) {
        this.text = text;
        index = -1;
        indexNext = 0;
        length = text.length();
    }

    public int getIndex() {
        return index;
    }

    public int getIndexNext() {
        return indexNext;
    }

    public int forward() {
        index = indexNext;
        if (index == length) {
            return TYPE_EOF;
        }
        char ch = text.charAt(index);
        switch (ch) {
            case '\n':
                indexNext = index + 1;
                return TYPE_NEWLINE;
            case '\r':
                if (index + 1 < length && text.charAt(index + 1) == '\n') {
                    indexNext = index + 2;
                } else {
                    indexNext = index + 1;
                }
                return TYPE_NEWLINE;
            default:
                indexNext = index + 1;
                while (indexNext < length) {
                    ch = text.charAt(indexNext);
                    if (ch == '\n' || ch == '\r') {
                        break;
                    }
                    indexNext++;
                }
                return TYPE_LINE_CONTENT;
        }
    }

    public void reset() {
        text = null;
        length = index = 0;
    }

}
