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
package io.github.rosemoe.sora.graphics;

import io.github.rosemoe.sora.util.IntPair;

/**
 * Utility for character position description, which is a packed (textOffset,pixelWidthOrOffset) value.
 *
 * @author Rosemoe
 */
public class CharPosDesc {

    private CharPosDesc() {

    }

    /**
     * Make a new character position description
     */
    public static long make(int textOffset, float pixelWidthOrOffset) {
        return IntPair.pack(textOffset, Float.floatToRawIntBits(pixelWidthOrOffset));
    }

    /**
     * Get character offset in text
     */
    public static int getTextOffset(long packedValue) {
        return IntPair.getFirst(packedValue);
    }

    /**
     * Get character width or offset in pixel
     */
    public static float getPixelWidthOrOffset(long packedValue) {
        return Float.intBitsToFloat(IntPair.getSecond(packedValue));
    }

}
