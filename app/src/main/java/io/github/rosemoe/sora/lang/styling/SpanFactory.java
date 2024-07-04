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
package io.github.rosemoe.sora.lang.styling;

import androidx.annotation.NonNull;

import java.util.Collection;

import io.github.rosemoe.sora.lang.styling.span.internal.SpanImpl;

/**
 * Factory for {@link Span}
 */
public class SpanFactory {

    private SpanFactory() {

    }

    /**
     * Get an available {@link Span} object from either cache or new instance.
     * The result object will be initialized with the given arguments.
     */
    @NonNull
    public static Span obtain(int column, long style) {
        return SpanImpl.obtain(column, style);
    }

    /**
     * Recycle all spans in the given collection
     */
    public static void recycleAll(@NonNull Collection<Span> spans) {
        for (Span span : spans) {
            if (!span.recycle()) {
                return;
            }
        }
    }

}
