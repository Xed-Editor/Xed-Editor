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

import android.util.SparseIntArray;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.Content;

/**
 * Collect brackets for simple languages. Not very effective. Not thread-safe.
 *
 * @author Rosemoe
 */
public class SimpleBracketsCollector implements BracketsProvider {

    private final SparseIntArray mapping;

    public SimpleBracketsCollector() {
        mapping = new SparseIntArray();
    }

    /**
     * Add new pair
     */
    public void add(int start, int end) {
        // add 1 to avoid zeros
        mapping.put(start + 1, end + 1);
        mapping.put(end + 1, start + 1);
    }

    /**
     * Remove all pairs
     */
    public void clear() {
        mapping.clear();
    }

    private PairedBracket getForIndex(int index) {
        int another = mapping.get(index + 1) - 1;
        if (another > index) {
            int tmp = index;
            index = another;
            another = tmp;
        }
        if (another != -1) {
            return new PairedBracket(index, another);
        }
        return null;
    }

    @Override
    public PairedBracket getPairedBracketAt(@NonNull Content text, int index) {
        var res = index - 1 >= 0 ? getForIndex(index - 1) : null;
        if (res == null) {
            res = getForIndex(index);
        }
        return res;
    }
}
