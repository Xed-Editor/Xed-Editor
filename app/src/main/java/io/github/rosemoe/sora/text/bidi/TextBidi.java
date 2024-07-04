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

import android.text.TextUtils;

import androidx.annotation.NonNull;

import java.text.Bidi;

import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.util.TemporaryCharBuffer;

/**
 * Text bidirectional utils. Some codes are from AOSP
 *
 * @author Rosemoe
 */
public class TextBidi {

    /**
     * Compute text directions for the given text
     */
    @NonNull
    public static Directions getDirections(@NonNull CharSequence text) {
        var len = text.length();
        if (doesNotNeedBidi(text)) {
            return new Directions(new long[]{IntPair.pack(0, 0)}, len);
        }
        var chars = TemporaryCharBuffer.obtain(len);
        TextUtils.getChars(text, 0, len, chars, 0);
        var bidi = new Bidi(chars, 0, null, 0, text.length(), Bidi.DIRECTION_LEFT_TO_RIGHT);
        var runs = new long[bidi.getRunCount()];
        for (int i = 0; i < runs.length; i++) {
            runs[i] = IntPair.pack(bidi.getRunStart(i), bidi.getRunLevel(i));
        }
        TemporaryCharBuffer.recycle(chars);
        return new Directions(runs, len);
    }

    public static boolean couldAffectRtl(char c) {
        return (0x0590 <= c && c <= 0x08FF) ||  // RTL scripts
                c == 0x200E ||  // Bidi format character
                c == 0x200F ||  // Bidi format character
                (0x202A <= c && c <= 0x202E) ||  // Bidi format characters
                (0x2066 <= c && c <= 0x2069) ||  // Bidi format characters
                (0xD800 <= c && c <= 0xDFFF) ||  // Surrogate pairs
                (0xFB1D <= c && c <= 0xFDFF) ||  // Hebrew and Arabic presentation forms
                (0xFE70 <= c && c <= 0xFEFE);  // Arabic presentation forms
    }

    /**
     * Returns true if there is no character present that may potentially affect RTL layout.
     * Since this calls couldAffectRtl() above, it's also quite conservative, in the way that
     * it may return 'false' (needs bidi) although careful consideration may tell us it should
     * return 'true' (does not need bidi).
     */
    public static boolean doesNotNeedBidi(@NonNull CharSequence text) {
        if (text instanceof BidiRequirementChecker) {
            return !((BidiRequirementChecker) text).mayNeedBidi();
        }
        final var len = text.length();
        for (int i = 0; i < len; i++) {
            if (couldAffectRtl(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }

}
