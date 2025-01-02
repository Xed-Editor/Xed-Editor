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

import static io.github.rosemoe.sora.lang.styling.TextStyle.isBold;
import static io.github.rosemoe.sora.lang.styling.TextStyle.isItalics;

import android.annotation.SuppressLint;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.bidi.Directions;
import io.github.rosemoe.sora.text.bidi.TextBidi;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.rendering.RenderContext;
import io.github.rosemoe.sora.widget.rendering.RenderingConstants;

/**
 * Manages graphical(actually measuring) operations of a text row
 */
public class GraphicTextRow {

    private final static GraphicTextRow[] sCached = new GraphicTextRow[5];
    private Paint paint;
    private ContentLine text;
    private RenderContext context;
    private Directions directions;
    private int line;
    private int textStart;
    private int textEnd;
    private int tabWidth;
    private List<Span> spans;
    private boolean useCache = true;
    private List<Integer> softBreaks;
    private boolean quickMeasureMode;
    private final Directions tmpDirections = new Directions(new long[]{IntPair.pack(0, 0)}, 0);

    private GraphicTextRow() {

    }

    public static GraphicTextRow obtain(boolean quickMeasure) {
        GraphicTextRow st;
        synchronized (sCached) {
            for (int i = sCached.length; --i >= 0; ) {
                if (sCached[i] != null) {
                    st = sCached[i];
                    sCached[i] = null;
                    st.quickMeasureMode = quickMeasure;
                    return st;
                }
            }
        }
        st = new GraphicTextRow();
        st.quickMeasureMode = quickMeasure;
        return st;
    }

    public static void recycle(GraphicTextRow st) {
        st.text = null;
        st.spans = null;
        st.paint = null;
        st.textStart = st.textEnd = st.tabWidth = st.line = 0;
        st.useCache = true;
        st.softBreaks = null;
        st.directions = null;
        st.context = null;
        synchronized (sCached) {
            for (int i = 0; i < sCached.length; ++i) {
                if (sCached[i] == null) {
                    sCached[i] = st;
                    break;
                }
            }
        }
    }

    public void recycle() {
        recycle(this);
    }

    public void set(@NonNull Content content, int line, int start, int end, @Nullable List<Span> spans, @NonNull Paint paint, @NonNull RenderContext context) {
        this.paint = paint;
        text = content.getLine(line);
        directions = text.mayNeedBidi() ? content.getLineDirections(line) : null;
        textStart = start;
        textEnd = end;
        this.spans = spans;
        tabWidth = context.getTabWidth();
        this.context = context;
        this.line = line;
        tmpDirections.setLength(text.length());
    }

    public void setSoftBreaks(@Nullable List<Integer> softBreaks) {
        this.softBreaks = softBreaks;
    }

    public void disableCache() {
        useCache = false;
    }

    /**
     * Build measure cache for the text
     */
    public void buildMeasureCache() {
        var cacheItem = context.getCache().getOrCreateMeasureCache(line);
        if (cacheItem.getWidths() == null || cacheItem.getWidths().length < textEnd + 4) {
            cacheItem.setWidths(new float[Math.max(90, text.length() + 16)]);
        }
        measureTextInternal(textStart, textEnd, cacheItem.getWidths());
        // Generate prefix sum
        var cache = cacheItem.getWidths();
        var pending = cache[0];
        cache[0] = 0f;
        for (int i = 1; i <= textEnd; i++) {
            var tmp = cache[i];
            cache[i] = cache[i - 1] + pending;
            pending = tmp;
        }
    }

    /**
     * From {@code start} to measure characters, until measured width add next char's width is bigger
     * than {@code advance}.
     * <p>
     * Note that the result array should not be stored.
     *
     * @return text offset and measured width
     * @see CharPosDesc Character position description
     */
    public long findOffsetByAdvance(int start, float advance) {
        if (useCache) {
            var cacheItem = context.getCache().queryMeasureCache(line);
            if (cacheItem != null && cacheItem.getWidths() != null) {
                var cache = cacheItem.getWidths();
                var end = textEnd;
                int left = start, right = end;
                var base = cache[start];
                while (left <= right) {
                    var mid = (left + right) / 2;
                    if (mid < start || mid >= end) {
                        left = mid;
                        break;
                    }
                    var value = cache[mid] - base;
                    if (value > advance) {
                        right = mid - 1;
                    } else if (value < advance) {
                        left = mid + 1;
                    } else {
                        left = mid;
                        break;
                    }
                }
                if (cache[left] - base > advance) {
                    left--;
                }
                left = Math.max(start, Math.min(end, left));
                return CharPosDesc.make(left, cache[left] - base);
            }
        }
        var regionItr = new TextRegionIterator(textEnd, spans, softBreaks);
        float currentPosition = 0f;
        // Find in each region
        var lastStyle = 0L;
        var chars = text.getBackingCharArray();
        float tabAdvance = paint.getSpaceWidth() * tabWidth;
        int offset = start;
        var first = true;
        while (regionItr.hasNextRegion() && currentPosition < advance) {
            if (first) {
                regionItr.requireStartOffset(start);
                first = false;
            } else {
                regionItr.nextRegion();
            }
            var regionStart = regionItr.getStartIndex();
            var regionEnd = regionItr.getEndIndex();
            regionEnd = Math.min(textEnd, regionEnd);
            var style = regionItr.getSpan().getStyleBits();
            if (style != lastStyle) {
                if (isBold(style) != isBold(lastStyle)) {
                    paint.setFakeBoldText(isBold(style));
                }
                if (isItalics(style) != isItalics(lastStyle)) {
                    paint.setTextSkewX(isItalics(style) ? RenderingConstants.TEXT_SKEW_X : 0f);
                }
                lastStyle = style;
            }

            // Find in subregion
            int res = -1;
            {
                int lastStart = regionStart;
                for (int i = regionStart; i < regionEnd; i++) {
                    if (chars[i] == '\t') {
                        // Here is a tab
                        // Try to find advance
                        if (lastStart != i) {
                            int idx = paint.findOffsetByRunAdvance(text, lastStart, i, advance - currentPosition, quickMeasureMode);
                            currentPosition += paint.measureTextRunAdvance(chars, lastStart, idx, regionStart, regionEnd, quickMeasureMode);
                            if (idx < i) {
                                res = idx;
                                break;
                            } else {
                                if (currentPosition + tabAdvance > advance) {
                                    res = i;
                                    break;
                                } else {
                                    currentPosition += tabAdvance;
                                }
                            }
                        } else {
                            if (currentPosition + tabAdvance > advance) {
                                res = i;
                                break;
                            } else {
                                currentPosition += tabAdvance;
                            }
                        }
                        lastStart = i + 1;
                    }
                }
                if (res == -1) {
                    int idx = paint.findOffsetByRunAdvance(text, lastStart, regionEnd, advance - currentPosition, quickMeasureMode);
                    currentPosition += measureText(lastStart, idx);
                    res = idx;
                }
            }

            offset = res;
            if (res < regionEnd) {
                break;
            }

            if (regionEnd == textEnd) {
                break;
            }
        }
        if (lastStyle != 0L) {
            paint.setFakeBoldText(false);
            paint.setTextSkewX(0f);
        }
        return CharPosDesc.make(offset, currentPosition);
    }

    public float measureText(int start, int end) {
        if (start < 0) {
            throw new IndexOutOfBoundsException("negative start position");
        }
        if (start >= end) {
            if (start != end)
                Log.w("GraphicTextRow", "start > end. if this is caused by editor, please provide feedback", new Throwable());
            return 0f;
        }
        if (useCache) {
            var cache = context.getCache().queryMeasureCache(line);
            if (cache != null && cache.getWidths() != null && end < cache.getWidths().length) {
                var widths = cache.getWidths();
                return widths[end] - widths[start];
            }
        }
        return measureTextInternal(start, end, null);
    }

    private float measureTextInternal(int start, int end, float[] widths) {
        // Backup values
        final var originalBold = paint.isFakeBoldText();
        final var originalSkew = paint.getTextSkewX();

        start = Math.max(start, textStart);
        end = Math.min(end, textEnd);
        var regionItr = new TextRegionIterator(end, spans, softBreaks);
        float width = 0f;
        // Measure for each region
        var lastStyle = 0L;
        var first = true;
        while (regionItr.hasNextRegion()) {
            if (first) {
                regionItr.requireStartOffset(start);
                first = false;
            } else {
                regionItr.nextRegion();
            }
            var regionStart = regionItr.getStartIndex();
            var regionEnd = regionItr.getEndIndex();
            regionEnd = Math.min(end, regionEnd);
            if (regionStart > regionEnd || (regionStart == regionEnd && regionEnd >= end)) {
                break;
            }
            var style = regionItr.getSpan().getStyleBits();
            if (style != lastStyle) {
                if (isBold(style) != isBold(lastStyle)) {
                    paint.setFakeBoldText(isBold(style));
                }
                if (isItalics(style) != isItalics(lastStyle)) {
                    paint.setTextSkewX(isItalics(style) ? RenderingConstants.TEXT_SKEW_X : 0f);
                }
                lastStyle = style;
            }
            int contextStart = Math.min(regionStart, regionItr.getSpanStart());
            int contextEnd = Math.max(regionEnd, regionItr.getSpanEnd());
            contextEnd = Math.min(textEnd, contextEnd);
            width += measureTextInner(regionStart, regionEnd, contextStart, contextEnd, widths);
            if (regionEnd >= end) {
                break;
            }
        }
        paint.setFakeBoldText(originalBold);
        paint.setTextSkewX(originalSkew);
        return width;
    }

    @SuppressLint("NewApi")
    private float measureTextInner(int start, int end, int ctxStart, int ctxEnd, float[] widths) {
        if (start >= end) {
            return 0f;
        }
        var dirs = directions == null ?
                (text.mayNeedBidi() ? TextBidi.getDirections(text) : tmpDirections)
                : directions;
        float width = 0;
        for (int i = 0; i < dirs.getRunCount(); i++) {
            int start1 = Math.max(start, dirs.getRunStart(i));
            int end1 = Math.min(end, dirs.getRunEnd(i));
            if (end1 > start1) {
                // Can be called directly
                width += paint.myGetTextRunAdvances(text.getBackingCharArray(), start1, end1 - start1, ctxStart, ctxEnd - ctxStart, dirs.isRunRtl(i), widths, widths == null ? 0 : start1, quickMeasureMode);
            }
            if (dirs.getRunStart(i) >= end) {
                break;
            }
        }
        float tabWidth = paint.getSpaceWidth() * this.tabWidth;
        int tabCount = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\t') {
                tabCount++;
                if (widths != null) {
                    widths[i] = tabWidth;
                }
            }
        }
        float extraWidth = tabCount == 0 ? 0 : tabWidth - paint.measureText("\t");
        return width + extraWidth * tabCount;
    }


}
