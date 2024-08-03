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
package io.github.rosemoe.sora.lang.styling.span;

import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;

public class SpanExtAttrs {
    /**
     * @see SpanColorResolver
     */
    public final static int EXT_COLOR_RESOLVER = 0;
    /**
     * @see SpanExternalRenderer
     */
    public final static int EXT_EXTERNAL_RENDERER = 1;
    /**
     * @see SpanInteractionInfo
     */
    public final static int EXT_INTERACTION_INFO = 2;
    /**
     * Set a {@link ResolvableColor} object for underline color resolving
     */
    public final static int EXT_UNDERLINE_COLOR = 3;

    public static boolean checkType(int extType, SpanExt ext) {
        if (ext == null) {
            return true;
        }
        switch (extType) {
            case EXT_COLOR_RESOLVER -> {
                return ext instanceof SpanColorResolver;
            }
            case EXT_EXTERNAL_RENDERER -> {
                return ext instanceof SpanExternalRenderer;
            }
            case EXT_INTERACTION_INFO -> {
                return ext instanceof SpanInteractionInfo;
            }
            case EXT_UNDERLINE_COLOR -> {
                return ext instanceof ResolvableColor;
            }
        }
        return true;
    }
}
