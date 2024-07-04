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
package io.github.rosemoe.sora.lang.styling.span.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.MutableIntObjectMap;

import java.util.Objects;

import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanPool;
import io.github.rosemoe.sora.lang.styling.color.ResolvableColor;
import io.github.rosemoe.sora.lang.styling.span.SpanExt;
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs;

public class SpanImpl implements Span {
    private final static SpanPool<SpanImpl> pool = new SpanPool<>(SpanImpl::new);

    private int column;
    private long style;
    private Object extra;
    private MutableIntObjectMap<SpanExt> extMap;

    SpanImpl() {

    }

    SpanImpl(int column, long style) {
        setColumn(column);
        setStyle(style);
    }

    public static SpanImpl obtain(int column, long style) {
        return pool.obtain(column, style);
    }

    @Override
    public void setColumn(int column) {
        this.column = column;
    }

    @Override
    public int getColumn() {
        return column;
    }

    @Override
    public void setStyle(long style) {
        this.style = style;
    }

    @Override
    public long getStyle() {
        return style;
    }

    @Override
    public void setUnderlineColor(@Nullable ResolvableColor color) {
        setSpanExt(SpanExtAttrs.EXT_UNDERLINE_COLOR, color);
    }

    @Nullable
    @Override
    public ResolvableColor getUnderlineColor() {
        return getSpanExt(SpanExtAttrs.EXT_UNDERLINE_COLOR);
    }

    @Override
    public void setExtra(Object extraData) {
        this.extra = extraData;
    }

    @Override
    public Object getExtra() {
        return extra;
    }

    @Override
    public void setSpanExt(int extType, @Nullable SpanExt ext) {
        if (!SpanExtAttrs.checkType(extType, ext)) {
            throw new IllegalArgumentException("type mismatch: extType " + extType + " and extObj " + ext);
        }
        if (ext == null) {
            if (extMap != null) {
                extMap.remove(extType);
            }
            return;
        }
        if (extMap == null) {
            extMap = new MutableIntObjectMap<>();
        }
        extMap.set(extType, ext);
    }

    @Override
    public boolean hasSpanExt(int extType) {
        return getSpanExt(extType) != null;
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T> T getSpanExt(int extType) {
        return extMap == null ? null : (T) extMap.get(extType);
    }

    @Override
    public void removeAllSpanExt() {
        if (extMap != null) {
            extMap.clear();
        }
    }

    @Override
    public void reset() {
        setColumn(0);
        setStyle(0L);
        removeAllSpanExt();
    }

    @NonNull
    @Override
    public Span copy() {
        var span = new SpanImpl();
        span.setColumn(getColumn());
        span.setStyle(getStyle());
        if (extMap != null) {
            span.extMap = new MutableIntObjectMap<>();
            span.extMap.putAll(extMap);
        }
        return span;
    }

    @Override
    public boolean recycle() {
        reset();
        return pool.offer(this);
    }

    @NonNull
    @Override
    public String toString() {
        return "SpanImpl{" +
                "column=" + column +
                ", style=" + style +
                ", extra=" + extra +
                ", extMap=" + extMap +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpanImpl span = (SpanImpl) o;
        return column == span.column && style == span.style && Objects.equals(extMap, span.extMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(column, style, extMap);
    }
}
