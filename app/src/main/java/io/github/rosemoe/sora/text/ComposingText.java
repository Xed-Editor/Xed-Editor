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

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage;

@UnsupportedUserUsage
public class ComposingText {

    public int startIndex, endIndex;
    public boolean preSetComposing;

    public void set(int start, int end) {
        this.startIndex = start;
        this.endIndex = end;
    }

    public void adjustLength(int length) {
        this.endIndex = startIndex + length;
    }

    public void reset() {
        this.startIndex = this.endIndex = -1;
    }

    public boolean isComposing() {
        var r = preSetComposing || startIndex >= 0 && endIndex >= 0;
        preSetComposing = false;
        return r;
    }

    public void shiftOnInsert(int insertStart, int insertEnd) {
        var length = insertEnd - insertStart;
        if (startIndex <= insertStart && endIndex >= insertStart) {
            endIndex += length;
        }
        // Type 2, text is inserted before a diagnostic
        if (startIndex > insertStart) {
            startIndex += length;
            endIndex += length;
        }
    }

    public void shiftOnDelete(int deleteStart, int deleteEnd) {
        var length = deleteEnd - deleteStart;
        // Compute cross length
        var sharedStart = Math.max(deleteStart, startIndex);
        var sharedEnd = Math.min(deleteEnd, endIndex);
        if (sharedEnd <= sharedStart) {
            // No shared region
            if (startIndex >= deleteEnd) {
                // Shift left
                startIndex -= length;
                endIndex -= length;
            }
        } else {
            // Has shared region
            var sharedLength = sharedEnd - sharedStart;
            endIndex -= sharedLength;
            if (startIndex > deleteStart) {
                // Shift left
                var shiftLeftCount = startIndex - deleteStart;
                startIndex -= shiftLeftCount;
                endIndex -= shiftLeftCount;
            }
        }
    }


}
