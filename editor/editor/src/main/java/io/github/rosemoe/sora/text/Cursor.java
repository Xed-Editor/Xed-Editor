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

import io.github.rosemoe.sora.util.IntPair;

/**
 * The cursor position will update automatically when the content has been changed by other ways.
 *
 * @author Rosemoe
 */
public final class Cursor {

    public final static int DIRECTION_NONE = 0;
    public final static int DIRECTION_LTR = 1;
    public final static int DIRECTION_RTL = 2;

    private final Content content;
    private final CachedIndexer indexer;
    private CharPosition leftSel, rightSel;
    private CharPosition cache0, cache1, cache2;
    private int selDirection = DIRECTION_NONE;

    /**
     * Create a new Cursor for Content
     *
     * @param content Target content
     */
    public Cursor(@NonNull Content content) {
        this.content = content;
        indexer = new CachedIndexer(content);
        leftSel = new CharPosition().toBOF();
        rightSel = new CharPosition().toBOF();
    }

    /**
     * Whether the given character is a white space character
     *
     * @param c Character to check
     * @return Result whether a space char
     */
    private static boolean isWhitespace(char c) {
        return (c == '\t' || c == ' ');
    }

    /**
     * Make left and right cursor on the given position
     *
     * @param line   The line position
     * @param column The column position
     */
    public void set(int line, int column) {
        setLeft(line, column);
        setRight(line, column);
    }

    /**
     * Make left cursor on the given position
     *
     * @param line   The line position
     * @param column The column position
     */
    public void setLeft(int line, int column) {
        leftSel = indexer.getCharPosition(line, column).fromThis();
    }

    /**
     * Make right cursor on the given position
     *
     * @param line   The line position
     * @param column The column position
     */
    public void setRight(int line, int column) {
        rightSel = indexer.getCharPosition(line, column).fromThis();
    }

    /**
     * Get the left cursor line
     *
     * @return line of left cursor
     */
    public int getLeftLine() {
        return leftSel.getLine();
    }

    /**
     * Get the left cursor column
     *
     * @return column of left cursor
     */
    public int getLeftColumn() {
        return leftSel.getColumn();
    }

    /**
     * Get the right cursor line
     *
     * @return line of right cursor
     */
    public int getRightLine() {
        return rightSel.getLine();
    }

    /**
     * Get the right cursor column
     *
     * @return column of right cursor
     */
    public int getRightColumn() {
        return rightSel.getColumn();
    }

    /**
     * Whether the given position is in selected region
     *
     * @param line   The line to query
     * @param column The column to query
     * @return Whether is in selected region
     */
    public boolean isInSelectedRegion(int line, int column) {
        if (line >= getLeftLine() && line <= getRightLine()) {
            boolean yes = true;
            if (line == getLeftLine()) {
                yes = column >= getLeftColumn();
            }
            if (line == getRightLine()) {
                yes = yes && column < getRightColumn();
            }
            return yes;
        }
        return false;
    }

    /**
     * Get the left cursor index
     *
     * @return index of left cursor
     */
    public int getLeft() {
        return leftSel.index;
    }

    /**
     * Get the right cursor index
     *
     * @return index of right cursor
     */
    public int getRight() {
        return rightSel.index;
    }

    /**
     * Notify the Indexer to update its cache for current display position
     * <p>
     * This will make querying actions quicker
     * <p>
     * Especially when the editor user want to set a new cursor position after scrolling long time
     *
     * @param line First visible line
     */
    public void updateCache(int line) {
        indexer.getCharIndex(line, 0);
    }

    /**
     * Get the using Indexer object
     *
     * @return Using Indexer
     */
    public CachedIndexer getIndexer() {
        return indexer;
    }

    /**
     * Get whether text is selected
     *
     * @return Whether selected
     */
    public boolean isSelected() {
        return leftSel.index != rightSel.index;
    }

    /**
     * Set current direction of selection.
     *
     * @see #getSelectionDirection()
     * @see #DIRECTION_NONE
     * @see #DIRECTION_LTR
     * @see #DIRECTION_RTL
     */
    public void setSelectionDirection(int selDirection) {
        this.selDirection = selDirection;
    }

    /**
     * Get current direction of selection
     *
     * @see #setSelectionDirection(int)
     */
    public int getSelectionDirection() {
        return selDirection;
    }

    /**
     * Get position after moving left once
     *
     * @param position A packed pair (line, column) describing the original position
     * @return A packed pair (line, column) describing the result position
     */
    public long getLeftOf(long position) {
        int line = IntPair.getFirst(position);
        int column = IntPair.getSecond(position);
        int n_column = TextLayoutHelper.get().getCurPosLeft(column, content.getLine(line));
        if (n_column == column && column == 0) {
            if (line == 0) {
                return 0;
            } else {
                int c_column = content.getColumnCount(line - 1);
                return IntPair.pack(line - 1, c_column);
            }
        } else {
            return IntPair.pack(line, n_column);
        }
    }

    /**
     * Get position after moving right once
     *
     * @param position A packed pair (line, column) describing the original position
     * @return A packed pair (line, column) describing the result position
     */
    public long getRightOf(long position) {
        int line = IntPair.getFirst(position);
        int column = IntPair.getSecond(position);
        int c_column = content.getColumnCount(line);
        int n_column = TextLayoutHelper.get().getCurPosRight(column, content.getLine(line));
        if (n_column == c_column && column == n_column) {
            if (line + 1 == content.getLineCount()) {
                return IntPair.pack(line, c_column);
            } else {
                return IntPair.pack(line + 1, 0);
            }
        } else {
            return IntPair.pack(line, n_column);
        }
    }

    /**
     * Get copy of left cursor
     */
    @NonNull
    public CharPosition left() {
        return leftSel.fromThis();
    }

    /**
     * Get copy of right cursor
     */
    @NonNull
    public CharPosition right() {
        return rightSel.fromThis();
    }

    /**
     * Get current range of cursor. Modifications to the returned object does not affect cursor positions.
     *
     * @return {@link TextRange} object describing cursor positions
     */
    public TextRange getRange() {
        return new TextRange(left(), right());
    }

    /**
     * Internal call back before insertion
     *
     * @param startLine   Start line
     * @param startColumn Start column
     */
    void beforeInsert(int startLine, int startColumn) {
        cache0 = indexer.getCharPosition(startLine, startColumn).fromThis();
    }

    /**
     * Internal call back before deletion
     *
     * @param startLine   Start line
     * @param startColumn Start column
     * @param endLine     End line
     * @param endColumn   End column
     */
    void beforeDelete(int startLine, int startColumn, int endLine, int endColumn) {
        cache1 = indexer.getCharPosition(startLine, startColumn).fromThis();
        cache2 = indexer.getCharPosition(endLine, endColumn).fromThis();
    }

    /**
     * Internal call back before replace
     */
    void beforeReplace() {
        indexer.beforeReplace(content);
    }

    /**
     * Internal call back after insertion
     *
     * @param startLine       Start line
     * @param startColumn     Start column
     * @param endLine         End line
     * @param endColumn       End column
     * @param insertedContent Inserted content
     */
    void afterInsert(int startLine, int startColumn, int endLine, int endColumn,
                     CharSequence insertedContent) {
        indexer.afterInsert(content, startLine, startColumn, endLine, endColumn, insertedContent);
        int beginIdx = cache0.getIndex();
        if (getLeft() >= beginIdx) {
            leftSel = indexer.getCharPosition(getLeft() + insertedContent.length()).fromThis();
        }
        if (getRight() >= beginIdx) {
            rightSel = indexer.getCharPosition(getRight() + insertedContent.length()).fromThis();
        }
    }

    /**
     * Internal call back
     *
     * @param startLine      Start line
     * @param startColumn    Start column
     * @param endLine        End line
     * @param endColumn      End column
     * @param deletedContent Deleted content
     */
    void afterDelete(int startLine, int startColumn, int endLine, int endColumn,
                     CharSequence deletedContent) {
        indexer.afterDelete(content, startLine, startColumn, endLine, endColumn, deletedContent);
        int beginIdx = cache1.getIndex();
        int endIdx = cache2.getIndex();
        int left = getLeft();
        int right = getRight();
        if (beginIdx > right) {
            return;
        }
        left = left - Math.max(0, Math.min(left - beginIdx, endIdx - beginIdx));
        right = right - Math.max(0, Math.min(right - beginIdx, endIdx - beginIdx));
        leftSel = indexer.getCharPosition(left).fromThis();
        rightSel = indexer.getCharPosition(right).fromThis();
    }

}

