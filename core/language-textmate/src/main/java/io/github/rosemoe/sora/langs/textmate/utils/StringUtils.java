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
package io.github.rosemoe.sora.langs.textmate.utils;

import java.util.regex.Pattern;

public class StringUtils {

    public static boolean checkSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isSurrogate(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    public static int convertUnicodeOffsetToUtf16(String text, int offset, boolean hasSurrogate) {
        if (hasSurrogate) {
            var j = 0;
            for (int i = 0; i < text.length(); i++) {
                if (j == offset) {
                    return i;
                }
                var ch = text.charAt(i);
                if (Character.isHighSurrogate(ch) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    i++;
                }
                j++;
            }
        }
        return offset;
    }


    private static final Pattern MATCH_PATTERN = Pattern.compile(".*/|\\..*");

    public static String getFileNameWithoutExtension(String filePath) {
        return MATCH_PATTERN.matcher(filePath).replaceAll("");

    }
}
