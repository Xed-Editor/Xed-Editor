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
package io.github.rosemoe.sora.lang.completion;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import io.github.rosemoe.sora.text.TextUtils;

/**
 * Utility class to provide some useful matching functions in generating completion.
 *
 * @author Rosemoe
 */
public class MatchHelper {

    /**
     * Color for matched text highlighting
     */
    public int highlightColor = 0xff3f51b5;
    /**
     * Case in-sensitive
     */
    public boolean ignoreCase = false;
    /**
     * Match case of first letter if ignoreCase=true
     * <p>
     * for {@link #startsWith(CharSequence, CharSequence)} only
     */
    public boolean matchFirstCase = false;

    public Spanned startsWith(CharSequence name, CharSequence pattern) {
        return startsWith(name, pattern, matchFirstCase, ignoreCase);
    }

    public Spanned startsWith(CharSequence name, CharSequence pattern, boolean matchFirstCase, boolean ignoreCase) {
        if (name.length() >= pattern.length()) {
            final var len = pattern.length();
            var matches = true;
            for (int i = 0; i < len; i++) {
                char a = name.charAt(i);
                char b = pattern.charAt(i);
                if (!(a == b || ((ignoreCase && (i != 0 || !matchFirstCase)) && Character.toLowerCase(a) == Character.toLowerCase(b)))) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                var spanned = new SpannableString(name);
                spanned.setSpan(new ForegroundColorSpan(highlightColor), 0, len, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                return spanned;
            }
        }
        return null;
    }

    public Spanned contains(CharSequence name, CharSequence pattern) {
        return contains(name, pattern, ignoreCase);
    }

    public Spanned contains(CharSequence name, CharSequence pattern, boolean ignoreCase) {
        int index = TextUtils.indexOf(name, pattern, ignoreCase, 0);
        if (index != -1) {
            var spanned = new SpannableString(name);
            spanned.setSpan(new ForegroundColorSpan(highlightColor), index, index + pattern.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spanned;
        }
        return null;
    }

    /**
     * Common sub-sequence
     */
    public Spanned commonSub(CharSequence name, CharSequence pattern) {
        return commonSub(name, pattern, ignoreCase);
    }

    /**
     * Common sub-sequence
     */
    public Spanned commonSub(CharSequence name, CharSequence pattern, boolean ignoreCase) {
        if (name.length() >= pattern.length()) {
            SpannableString spanned = null;
            var len = pattern.length();
            int j = 0;
            for (int i = 0; i < len; i++) {
                char p = pattern.charAt(i);
                var matched = false;
                for (; j < name.length() && !matched; j++) {
                    char s = name.charAt(j);
                    if (s == j || (ignoreCase && Character.toLowerCase(s) == Character.toLowerCase(p))) {
                        matched = true;
                        if (spanned == null) {
                            spanned = new SpannableString(name);
                        }
                        spanned.setSpan(new ForegroundColorSpan(highlightColor), j, j + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
                if (!matched) {
                    return null;
                }
            }
            return spanned;
        }
        return null;
    }

}
