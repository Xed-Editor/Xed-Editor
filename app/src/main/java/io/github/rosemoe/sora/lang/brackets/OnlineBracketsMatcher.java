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
package io.github.rosemoe.sora.lang.brackets;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.Content;

/**
 * Compute paired bracket when queried
 *
 * @author Rosemoe
 */
public class OnlineBracketsMatcher implements BracketsProvider {

    private final char[] pairs;
    private final int limit;

    /**
     * @param pairs Pairs. For example: {'(', ')', '{', '}'}
     * @param limit Max length to search
     */
    public OnlineBracketsMatcher(char[] pairs, int limit) {
        if ((pairs.length & 1) != 0) {
            throw new IllegalArgumentException("pairs must have even length");
        }
        this.pairs = pairs;
        this.limit = limit;
    }

    private int findIndex(char ch) {
        for (int i = 0; i < pairs.length; i++) {
            if (ch == pairs[i]) {
                return i;
            }
        }
        return -1;
    }

    private PairedBracket tryComputePaired(Content text, int index) {
        char a = text.charAt(index);
        int symbolIndex = findIndex(a);
        if (symbolIndex != -1) {
            char b = pairs[symbolIndex ^ 1];
            int stack = 0;
            if ((symbolIndex & 1) == 0) {
                // Find forward
                for (int i = index + 1; i < text.length() && i - index < limit; i++) {
                    char ch = text.charAt(i);
                    if (ch == b) {
                        if (stack <= 0) {
                            return new PairedBracket(index, i);
                        } else {
                            stack--;
                        }
                    } else if (ch == a) {
                        stack++;
                    }
                }
            } else {
                // Find backward
                for (int i = index - 1; i >= 0 && index - i < limit; i--) {
                    char ch = text.charAt(i);
                    if (ch == b) {
                        if (stack <= 0) {
                            return new PairedBracket(i, index);
                        } else {
                            stack--;
                        }
                    } else if (ch == a) {
                        stack++;
                    }
                }
            }
        }
        return null;
    }

    @Override
    public PairedBracket getPairedBracketAt(@NonNull Content text, int index) {
        PairedBracket pairedBracket = null;
        if (index > 0) {
            pairedBracket = tryComputePaired(text, index - 1);
        }
        if (pairedBracket == null) {
            pairedBracket = tryComputePaired(text, index);
        }
        return pairedBracket;
    }
}
