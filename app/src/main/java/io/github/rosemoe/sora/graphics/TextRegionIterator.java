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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.util.RegionIterator;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Helper class for {@link GraphicTextRow} to iterate text regions
 *
 * @author Rosemoe
 */
class TextRegionIterator extends RegionIterator {

    private final List<Span> spans;

    public TextRegionIterator(int length, @NonNull List<Span> spans, @Nullable List<Integer> softBreaks) {
        super(length, new SpansPoints(spans), new SoftBreaksPoints(softBreaks));
        this.spans = spans;
    }

    /**
     * Move to next region, until the end index of that region is bigger than {@code index}.
     * And set region start index to {@code index}.
     */
    public void requireStartOffset(int index) {
        if (index > getMax()) {
            throw new IllegalArgumentException();
        }
        if (getStartIndex() != 0) {
            throw new IllegalStateException();
        }
        do {
            nextRegion();
        } while (getEndIndex() <= index && hasNextRegion());
        startIndex = index;
    }

    /**
     * Get current {@link Span} for current region
     */
    public Span getSpan() {
        var idx = getRegionSourcePointer(0) - 1;
        if (idx < 0) {
            return SpanFactory.obtain(0, EditorColorScheme.TEXT_NORMAL);
        }
        return spans.get(idx);
    }

    /**
     * Get start index of current {@link Span}
     */
    public int getSpanStart() {
        return getPointerValue(0, getRegionSourcePointer(0) - 1);
    }

    /**
     * Get end index of current {@link Span}
     */
    public int getSpanEnd() {
        return getPointerValue(0, getRegionSourcePointer(0));
    }

    private static class SpansPoints implements RegionProvider {

        private final List<Span> spans;

        public SpansPoints(List<Span> spans) {
            this.spans = spans;
        }

        @Override
        public int getPointCount() {
            return spans == null ? 0 : spans.size();
        }

        @Override
        public int getPointAt(int index) {
            return spans.get(index).getColumn();
        }

    }

    private static class SoftBreaksPoints implements RegionProvider {

        private final List<Integer> points;

        public SoftBreaksPoints(List<Integer> points) {
            this.points = points;
        }

        @Override
        public int getPointCount() {
            return points == null ? 0 : points.size();
        }

        @Override
        public int getPointAt(int index) {
            return points.get(index);
        }
    }

}
