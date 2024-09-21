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

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.rosemoe.sora.annotations.UnsupportedUserUsage;

/**
 * Indexer Impl for Content
 * With cache
 *
 * @author Rose
 */
public class CachedIndexer implements Indexer, ContentListener {

    private final Content content;
    private final CharPosition startPosition = new CharPosition().toBOF();
    private final CharPosition endPosition = new CharPosition();
    private final List<CharPosition> cachedPositions = new ArrayList<>();
    private final int thresholdLine = 50;
    private int thresholdIndex = 50;
    private int maxCacheCount = 50;

    /**
     * Create a new CachedIndexer for the given content
     *
     * @param content Content to manage
     */
    CachedIndexer(@NonNull Content content) {
        this.content = content;
        updateEnd();
    }

    /**
     * If the querying index is larger than the switch
     * We will add its result to cache
     *
     * @param s Switch
     */
    public void setThresholdIndex(int s) {
        thresholdIndex = s;
    }

    /**
     * Update the end position
     */
    private void updateEnd() {
        endPosition.index = content.length();
        endPosition.line = content.getLineCount() - 1;
        endPosition.column = content.getColumnCount(endPosition.line);
    }

    /**
     * Get the nearest cache for the given index
     *
     * @param index Querying index
     * @return Nearest cache
     */
    @NonNull
    private synchronized CharPosition findNearestByIndex(int index) {
        int min = index, dis = index;
        CharPosition nearestCharPosition = startPosition;
        int targetIndex = 0;
        for (int i = 0; i < cachedPositions.size(); i++) {
            CharPosition pos = cachedPositions.get(i);
            dis = Math.abs(pos.index - index);
            if (dis < min) {
                min = dis;
                nearestCharPosition = pos;
                targetIndex = i;
            }
            if (dis <= thresholdIndex) {
                break;
            }
        }
        if (Math.abs(endPosition.index - index) < dis) {
            nearestCharPosition = endPosition;
        }
        if (nearestCharPosition != startPosition && nearestCharPosition != endPosition) {
            Collections.swap(cachedPositions, targetIndex, 0);
        }
        return nearestCharPosition;
    }

    /**
     * Get the nearest cache for the given line
     *
     * @param line Querying line
     * @return Nearest cache
     */
    @NonNull
    private synchronized CharPosition findNearestByLine(int line) {
        int min = line, dis = line;
        CharPosition nearestCharPosition = startPosition;
        int targetIndex = 0;
        for (int i = 0; i < cachedPositions.size(); i++) {
            CharPosition pos = cachedPositions.get(i);
            dis = Math.abs(pos.line - line);
            if (dis < min) {
                min = dis;
                nearestCharPosition = pos;
                targetIndex = i;
            }
            if (min <= thresholdLine) {
                break;
            }
        }
        if (Math.abs(endPosition.line - line) < dis) {
            nearestCharPosition = endPosition;
        }
        if (nearestCharPosition != startPosition && nearestCharPosition != endPosition) {
            Collections.swap(cachedPositions, 0, targetIndex);
        }
        return nearestCharPosition;
    }

    /**
     * From the given position to find forward in text
     *
     * @param start Given position
     * @param index Querying index
     */
    @VisibleForTesting
    void findIndexForward(@NonNull CharPosition start, int index, @NonNull CharPosition dest) {
        if (start.index > index) {
            throw new IllegalArgumentException("Unable to find backward from method findIndexForward()");
        }
        int workLine = start.line;
        int workColumn = start.column;
        int workIndex = start.index;
        //Move the column to the line end
        {
            var addition = Math.max(content.getLineSeparatorUnsafe(workLine).getLength() - 1, 0);
            int column = content.getColumnCountUnsafe(workLine) + addition;
            workIndex += column - workColumn;
            workColumn = column;
        }
        while (workIndex < index) {
            workLine++;
            var addition = Math.max(content.getLineSeparatorUnsafe(workLine).getLength() - 1, 0);
            workColumn = content.getColumnCountUnsafe(workLine) + addition;
            workIndex += workColumn + 1;
        }
        if (workIndex > index) {
            workColumn -= workIndex - index;
        }
        dest.column = workColumn;
        dest.line = workLine;
        dest.index = index;
    }

    /**
     * From the given position to find backward in text
     *
     * @param start Given position
     * @param index Querying index
     */
    @VisibleForTesting
    void findIndexBackward(@NonNull CharPosition start, int index, @NonNull CharPosition dest) {
        if (start.index < index) {
            throw new IllegalArgumentException("Unable to find forward from method findIndexBackward()");
        }
        int workLine = start.line;
        int workColumn = start.column;
        int workIndex = start.index;
        while (workIndex > index) {
            workIndex -= workColumn + 1;
            workLine--;
            if (workLine != -1) {
                var addition = Math.max(content.getLineSeparatorUnsafe(workLine).getLength() - 1, 0);
                workColumn = content.getColumnCountUnsafe(workLine) + addition;
            } else {
                // Reached the start of text,we have to use findIndexForward() as this method can not handle it
                findIndexForward(startPosition, index, dest);
                return;
            }
        }
        int dColumn = index - workIndex;
        if (dColumn > 0) {
            workLine++;
            workColumn = dColumn - 1;
        }
        dest.column = workColumn;
        dest.line = workLine;
        dest.index = index;
    }

    /**
     * From the given position to find forward in text
     *
     * @param start  Given position
     * @param line   Querying line
     * @param column Querying column
     */
    @VisibleForTesting
    void findLiCoForward(@NonNull CharPosition start, int line, int column, @NonNull CharPosition dest) {
        if (start.line > line) {
            throw new IllegalArgumentException("can not find backward from findLiCoForward()");
        }
        int workLine = start.line;
        int workIndex = start.index;
        {
            //Make index to left of line
            workIndex = workIndex - start.column;
        }
        while (workLine < line) {
            workIndex += content.getColumnCountUnsafe(workLine) + content.getLineSeparatorUnsafe(workLine).getLength();
            workLine++;
        }
        dest.column = 0;
        dest.line = workLine;
        dest.index = workIndex;
        findInLine(dest, line, column);
    }

    /**
     * From the given position to find backward in text
     *
     * @param start  Given position
     * @param line   Querying line
     * @param column Querying column
     */
    @VisibleForTesting
    void findLiCoBackward(@NonNull CharPosition start, int line, int column, @NonNull CharPosition dest) {
        if (start.line < line) {
            throw new IllegalArgumentException("can not find forward from findLiCoBackward()");
        }
        int workLine = start.line;
        int workIndex = start.index;
        {
            //Make index to the left of line
            workIndex = workIndex - start.column;
        }
        while (workLine > line) {
            workIndex -= content.getColumnCountUnsafe(workLine - 1) + content.getLineSeparatorUnsafe(workLine - 1).getLength();
            workLine--;
        }
        dest.column = 0;
        dest.line = workLine;
        dest.index = workIndex;
        findInLine(dest, line, column);
    }

    /**
     * From the given position to find in this line
     *
     * @param pos    Given position
     * @param line   Querying line
     * @param column Querying column
     */
    private void findInLine(@NonNull CharPosition pos, int line, int column) {
        if (pos.line != line) {
            throw new IllegalArgumentException("can not find other lines with findInLine()");
        }
        pos.index = pos.index - pos.column + column;
        pos.column = column;
    }

    /**
     * Add new cache
     *
     * @param pos New cache
     */
    private synchronized void push(@NonNull CharPosition pos) {
        if (maxCacheCount <= 0) {
            return;
        }
        cachedPositions.add(pos);
        if (cachedPositions.size() > maxCacheCount) {
            cachedPositions.remove(0);
        }
    }

    /**
     * Get max cache size
     *
     * @return max cache size
     */
    protected int getMaxCacheCount() {
        return maxCacheCount;
    }

    /**
     * Set max cache size
     *
     * @param maxSize max cache size
     */
    protected void setMaxCacheCount(int maxSize) {
        maxCacheCount = maxSize;
    }

    @Override
    public int getCharIndex(int line, int column) {
        return getCharPosition(line, column).index;
    }

    @Override
    public int getCharLine(int index) {
        return getCharPosition(index).line;
    }

    @Override
    public int getCharColumn(int index) {
        return getCharPosition(index).column;
    }

    @NonNull
    @Override
    public CharPosition getCharPosition(int index) {
        var pos = new CharPosition();
        getCharPosition(index, pos);
        return pos;
    }

    @Override
    public void getCharPosition(int index, @NonNull CharPosition dest) {
        content.checkIndex(index);
        content.lock(false);
        try {
            CharPosition pos = findNearestByIndex(index);
            if (pos.index == index) {
                dest.set(pos);
            } else if (pos.index < index) {
                findIndexForward(pos, index, dest);
            } else {
                findIndexBackward(pos, index, dest);
            }
            if (Math.abs(index - pos.index) >= thresholdIndex) {
                push(dest.fromThis());
            }
        } finally {
            content.unlock(false);
        }
    }

    @NonNull
    @Override
    public CharPosition getCharPosition(int line, int column) {
        var pos = new CharPosition();
        getCharPosition(line, column, pos);
        return pos;
    }

    @Override
    public void getCharPosition(int line, int column, @NonNull CharPosition dest) {
        content.checkLineAndColumn(line, column);
        content.lock(false);
        try {
            CharPosition pos = findNearestByLine(line);
            if (pos.line == line) {
                dest.set(pos);
                if (pos.column == column) {
                    return;
                }
                findInLine(dest, line, column);
            } else if (pos.line < line) {
                findLiCoForward(pos, line, column, dest);
            } else {
                findLiCoBackward(pos, line, column, dest);
            }
            if (Math.abs(pos.line - line) > thresholdLine) {
                push(dest.fromThis());
            }
        } finally {
            content.unlock(false);
        }
    }

    @Override
    @UnsupportedUserUsage
    public void beforeReplace(@NonNull Content content) {
        //Do nothing
    }

    @Override
    @UnsupportedUserUsage
    public synchronized void afterInsert(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn,
                                         @NonNull CharSequence insertedContent) {
        for (var pos : cachedPositions) {
            if (pos.line == startLine) {
                if (pos.column >= startColumn) {
                    pos.index += insertedContent.length();
                    pos.line += endLine - startLine;
                    pos.column = endColumn + pos.column - startColumn;
                }
            } else if (pos.line > startLine) {
                pos.index += insertedContent.length();
                pos.line += endLine - startLine;
            }
        }
        updateEnd();
    }

    @Override
    @UnsupportedUserUsage
    public synchronized void afterDelete(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn,
                                         @NonNull CharSequence deletedContent) {
        List<CharPosition> garbage = new ArrayList<>();
        for (CharPosition pos : cachedPositions) {
            if (pos.line == startLine) {
                if (pos.column >= startColumn)
                    garbage.add(pos);
            } else if (pos.line > startLine) {
                if (pos.line < endLine) {
                    garbage.add(pos);
                } else if (pos.line == endLine) {
                    garbage.add(pos);
                } else {
                    pos.index -= deletedContent.length();
                    pos.line -= endLine - startLine;
                }
            }
        }
        cachedPositions.removeAll(garbage);
        updateEnd();
    }

}

