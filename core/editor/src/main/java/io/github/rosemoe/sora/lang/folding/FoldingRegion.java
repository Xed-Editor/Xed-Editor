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
package io.github.rosemoe.sora.lang.folding;

import java.util.List;

import io.github.rosemoe.sora.util.IntPair;

/**
 * Indicates a folding region
 */
public class FoldingRegion {

    private long start;
    private long end;
    private boolean collapsed;
    private List<FoldingRegion> children;

    public FoldingRegion(int startLine, int startColumn, int endLine, int endColumn) {
        this(IntPair.pack(startLine, startColumn), IntPair.pack(endLine, endColumn));
        if (startLine > endLine || (startLine == endLine && startColumn > endColumn)) {
            throw new IllegalArgumentException("start > end");
        }
    }

    FoldingRegion(long start, long end) {
        this.start = start;
        this.end = end;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public int getStartLine() {
        return IntPair.getFirst(start);
    }

    public int getStartColumn() {
        return IntPair.getSecond(start);
    }

    public int getEndLine() {
        return IntPair.getFirst(end);
    }

    public int getEndColumn() {
        return IntPair.getSecond(end);
    }

    public FoldingRegion createChild(int startLine, int startColumn, int endLine, int endColumn) {
        if (startLine < getStartLine() || (startLine == getStartLine() && startColumn < getStartColumn())) {
            throw new IllegalArgumentException("child start is before parent start");
        }
        if (endLine > getEndLine() || (endLine == getEndLine() && endColumn > getEndColumn())) {
            throw new IllegalArgumentException("child end is beyond parent end");
        }
        var child = new FoldingRegion(startLine, startColumn, endLine, endColumn);
        children.add(child);
        return child;
    }

}
