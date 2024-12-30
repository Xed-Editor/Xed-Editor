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

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.DynamicLayout;
import android.text.Editable;
import android.text.Layout;
import android.text.Selection;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import androidx.annotation.NonNull;

/**
 * Helper class for indirectly calling Paint#getTextRunCursor(), which is
 * responsible for cursor controlling.
 *
 * @author Rosemoe
 */
public class TextLayoutHelper {

    private static final ThreadLocal<TextLayoutHelper> sLocal;

    static {
        sLocal = new ThreadLocal<>();
    }

    private final Editable text = Editable.Factory.getInstance().newEditable("");
    private final DynamicLayout layout;
    private final static int CHAR_FACTOR = 64;

    private TextLayoutHelper() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            layout = new DynamicLayout(text, new TextPaint(), Integer.MAX_VALUE / 2,
                    Layout.Alignment.ALIGN_NORMAL, 0, 0, true);
            try {
                @SuppressLint({"DiscouragedPrivateApi", "SoonBlockedPrivateApi"})
                var field = Layout.class.getDeclaredField("mTextDir");
                field.setAccessible(true);
                field.set(layout, TextDirectionHeuristics.LTR);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            layout = DynamicLayout.Builder.obtain(text, new TextPaint(), Integer.MAX_VALUE / 2)
                    .setIncludePad(true)
                    .setLineSpacing(0, 0)
                    .setTextDirection(TextDirectionHeuristics.LTR)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build();
        }
    }

    /**
     * Get TextLayoutHelper for current thread
     */
    public static TextLayoutHelper get() {
        var v = sLocal.get();
        if (v == null) {
            v = new TextLayoutHelper();
            sLocal.set(v);
        }
        return v;
    }

    /**
     * Get cursor position after moving left
     */
    public int getCurPosLeft(int offset, @NonNull CharSequence s) {
        int left = Math.max(0, offset - CHAR_FACTOR);
        int index = offset - left;
        text.append(s, left, Math.min(s.length(), offset + CHAR_FACTOR + 1));
        index = Math.min(index, text.length());
        Selection.setSelection(text, index);
        Selection.moveLeft(text, layout);
        index = Selection.getSelectionStart(text);
        text.clear();
        Selection.removeSelection(text);
        return left + index;
    }

    /**
     * Get cursor position after moving right
     */
    public int getCurPosRight(int offset, @NonNull CharSequence s) {
        int left = Math.max(0, offset - CHAR_FACTOR);
        int index = offset - left;
        text.append(s, left, Math.min(s.length(), offset + CHAR_FACTOR + 1));
        index = Math.min(index, text.length());
        Selection.setSelection(text, index);
        Selection.moveRight(text, layout);
        index = Selection.getSelectionStart(text);
        text.clear();
        Selection.removeSelection(text);
        return left + index;
    }

}
