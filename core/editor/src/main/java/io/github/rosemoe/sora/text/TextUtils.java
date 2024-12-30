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
 * Utility class for texts
 */
public class TextUtils {

    /**
     * Counts the number of whitespaces at the start of the given {@link CharSequence}.
     *
     * @param text     The text to count the spaces in.
     * @return A long packed with the number of spaces and tabs at the start of the line.
     * Use {@link IntPair#getFirst(long)} to get the number of spaces and {@link IntPair#getSecond(long)}
     * for the number of tabs.
     */
    public static long countLeadingSpacesAndTabs(@NonNull CharSequence text) {
        Objects.requireNonNull(text);

        int p = 0, spaces = 0, tabs = 0;
        char c;
        while (p < text.length() && isWhitespace((c = text.charAt(p)))) {
            if (c == '\t') {
                tabs += 1;
            } else {
                spaces += 1;
            }
            ++p;
        }

        return IntPair.pack(spaces, tabs);
    }

    /**
     * Compute leading space count
     *
     * @param tabWidth Tab is considered in {@code tabWidth} spaces
     */
    public static int countLeadingSpaceCount(@NonNull CharSequence text, int tabWidth) {
        final var result = countLeadingSpacesAndTabs(text);
        return IntPair.getFirst(result) + (tabWidth * IntPair.getSecond(result));
    }

    /**
     * Create indent space
     *
     * @return Generated space string
     */
    public static String createIndent(int indentSize, int tabWidth, boolean useTab) {
        indentSize = Math.max(0, indentSize);
        int tab = 0;
        int space;
        if (useTab) {
            tab = indentSize / tabWidth;
            space = indentSize % tabWidth;
        } else {
            space = indentSize;
        }
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < tab; i++) {
            s.append('\t');
        }
        for (int i = 0; i < space; i++) {
            s.append(' ');
        }
        return s.toString();
    }

    public static int indexOf(@NonNull CharSequence text, @NonNull CharSequence pattern, boolean ignoreCase, int fromIndex) {
        var max = text.length() - pattern.length();
        var len = pattern.length();
        label:
        for (int i = fromIndex; i <= max; i++) {
            // Compare
            for (int j = 0; j < len; j++) {
                char s = text.charAt(i + j);
                char p = pattern.charAt(j);
                if (!(s == p || (ignoreCase && Character.toLowerCase(s) == Character.toLowerCase(p)))) {
                    continue label;
                }
            }
            return i;
        }
        return -1;
    }

    public static int lastIndexOf(@NonNull CharSequence text, @NonNull CharSequence pattern, boolean ignoreCase, int fromIndex) {
        var len = pattern.length();
        fromIndex = Math.min(fromIndex, text.length() - len);
        label:
        for (int i = fromIndex; i >= 0; i--) {
            // Compare
            for (int j = 0; j < len; j++) {
                char s = text.charAt(i + j);
                char p = pattern.charAt(j);
                if (!(s == p || (ignoreCase && Character.toLowerCase(s) == Character.toLowerCase(p)))) {
                    continue label;
                }
            }
            return i;
        }
        return -1;
    }

    public static boolean startsWith(@NonNull CharSequence text, @NonNull CharSequence pattern, boolean ignoreCase) {
        if (text.length() < pattern.length()) {
            return false;
        }
        var len = pattern.length();
        for (int i = 0; i < len; i++) {
            char s = text.charAt(i);
            char p = pattern.charAt(i);
            if (!((s == p) || (ignoreCase && Character.toLowerCase(s) == Character.toLowerCase(p)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWhitespace(char ch) {
        return ch == '\t' || ch == ' ';
    }

    public static String padStart(String src, char padChar, int length) {
        if (src.length() >= length) {
            return src;
        }
        var sb = new StringBuilder(length);
        for (int i = 0; i < length - src.length(); i++) {
            sb.append(padChar);
        }
        sb.append(src);
        return sb.toString();
    }

    /**
     * As the name is, we find where leading spaces end and trailing spaces start
     *
     * @param line The line to search
     */
    public static long findLeadingAndTrailingWhitespacePos(ContentLine line) {
        var buffer = line.getBackingCharArray();
        int column = line.length();
        int leading = 0;
        int trailing = column;
        while (leading < column && isWhitespace(buffer[leading])) {
            leading++;
        }
        // Only them this action is needed
        if (leading != column) {
            while (trailing > 0 && isWhitespace(buffer[trailing - 1])) {
                trailing--;
            }
        }
        return IntPair.pack(leading, trailing);
    }
}
