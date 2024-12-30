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
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import io.github.rosemoe.sora.text.bidi.ContentBidi;
import io.github.rosemoe.sora.text.bidi.Directions;

/**
 * This class saves the text content for editor and maintains line widths.
 * It is thread-safe by default. Use {@link #Content(CharSequence, boolean)} constructor to
 * create a non thread-safe one.
 *
 * @author Rosemoe
 */
public class Content implements CharSequence {

    public final static int DEFAULT_MAX_UNDO_STACK_SIZE = 500;
    public final static int DEFAULT_LIST_CAPACITY = 1000;

    private static int sInitialListCapacity;

    static {
        setInitialLineCapacity(DEFAULT_LIST_CAPACITY);
    }

    private final List<ContentLine> lines;
    private final List<ContentListener> contentListeners;
    private final ReadWriteLock lock;
    private int textLength;
    private int nestedBatchEdit;
    private final AtomicLong documentVersion = new AtomicLong(1L);
    private final Indexer indexer;
    private final ContentBidi bidi;
    private UndoManager undoManager;
    private Cursor cursor;

    /**
     * This constructor will create a Content object with no text
     */
    public Content() {
        this(null);
    }

    /**
     * This constructor will create a Content object with the given source.
     * If you give us null,it will just create an empty Content object
     *
     * @param src The source of Content
     */
    public Content(CharSequence src) {
        this(src, true);
    }

    /**
     * Create a Content object with the given content text. Specify whether thread-safe access
     * to single instance is enabled.
     */
    public Content(CharSequence src, boolean threadSafe) {
        if (src == null) {
            src = "";
        }
        if (threadSafe) {
            lock = new ReentrantReadWriteLock();
        } else {
            lock = null;
        }
        textLength = 0;
        nestedBatchEdit = 0;
        lines = new ArrayList<>(getInitialLineCapacity());
        lines.add(new ContentLine());
        contentListeners = new ArrayList<>();
        bidi = new ContentBidi(this);
        undoManager = new UndoManager();
        setMaxUndoStackSize(Content.DEFAULT_MAX_UNDO_STACK_SIZE);
        indexer = new CachedIndexer(this);
        if (src.length() == 0) {
            setUndoEnabled(true);
            return;
        }
        setUndoEnabled(false);
        insert(0, 0, src);
        setUndoEnabled(true);
    }

    /**
     * Returns the default capacity of text line list
     *
     * @return Default capacity
     */
    public static int getInitialLineCapacity() {
        return Content.sInitialListCapacity;
    }

    /**
     * Set the default capacity of text line list
     *
     * @param capacity Default capacity
     */
    public static void setInitialLineCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity can not be negative or zero");
        }
        sInitialListCapacity = capacity;
    }

    /**
     * Test whether the two ContentLine have the same text
     *
     * @param a ContentLine
     * @param b another ContentLine
     * @return Whether the text in the given two lines equal
     */
    private static boolean textEquals(@NonNull ContentLine a, @NonNull ContentLine b) {
        if (a.length() != b.length()) {
            return false;
        }
        if (a == b) {
            return true;
        }
        for (int i = 0; i < a.length(); i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    public boolean isThreadSafe() {
        return lock != null;
    }

    protected void lock(boolean write) {
        if (lock == null) {
            return;
        }
        (write ? lock.writeLock() : lock.readLock()).lock();
    }

    protected void unlock(boolean write) {
        if (lock == null) {
            return;
        }
        (write ? lock.writeLock() : lock.readLock()).unlock();
    }

    @Override
    public char charAt(int index) {
        checkIndex(index);
        lock(false);
        try {
            var p = getIndexer().getCharPosition(index);
            return lines.get(p.line).charAt(p.column);
        } finally {
            unlock(false);
        }
    }

    /**
     * Get the character at the given position
     *
     * @param line   The line position of character
     * @param column The column position of character
     * @return The character at the given position
     */
    public char charAt(int line, int column) {
        lock(false);
        try {
            checkLineAndColumn(line, column);
            return lines.get(line).charAt(column);
        } finally {
            unlock(false);
        }
    }

    @Override
    public int length() {
        return textLength;
    }

    @NonNull
    @Override
    public CharSequence subSequence(int start, int end) {
        if (start > end) {
            throw new StringIndexOutOfBoundsException("start > end");
        }
        lock(false);
        try {
            var s = getIndexer().getCharPosition(start);
            var e = getIndexer().getCharPosition(end);
            return subContentInternal(s.getLine(), s.getColumn(), e.getLine(), e.getColumn());
        } finally {
            unlock(false);
        }
    }

    public String substring(int start, int end) {
        if (start > end) {
            throw new StringIndexOutOfBoundsException("start > end");
        }
        lock(false);
        try {
            var s = getIndexer().getCharPosition(start);
            var e = getIndexer().getCharPosition(end);
            return subStringBuilder(s.getLine(), s.getColumn(), e.getLine(), e.getColumn(), end - start + 1).toString();
        } finally {
            unlock(false);
        }
    }

    /**
     * Get raw data of line.
     * The result should not be modified by code out of editor framework.
     *
     * @param line Line
     * @return Raw ContentLine used by Content
     */
    public ContentLine getLine(int line) {
        lock(false);
        try {
            return lines.get(line);
        } finally {
            unlock(false);
        }
    }

    /**
     * Get how many lines there are
     *
     * @return Line count
     */
    public int getLineCount() {
        return lines.size();
    }

    /**
     * Get how many characters is on the given line
     * If (line < 0 or line >= getLineCount()),it will throw a IndexOutOfBoundsException
     *
     * @param line The line to get
     * @return Character count on line
     */
    public int getColumnCount(int line) {
        return getLine(line).length();
    }

    /**
     * Get the given line text without '\n' character
     *
     * @param line The line to get
     * @return New String object of this line
     */
    public String getLineString(int line) {
        lock(false);
        try {
            checkLine(line);
            return lines.get(line).toString();
        } finally {
            unlock(false);
        }
    }

    /**
     * Get region of the given line
     *
     * @param dest   Destination of characters
     * @param offset Offset in dest to store the chars
     */
    public void getRegionOnLine(int line, int start, int end, char[] dest, int offset) {
        lock(false);
        try {
            lines.get(line).getChars(start, end, dest, offset);
        } finally {
            unlock(false);
        }
    }

    /**
     * Get characters of line
     */
    public void getLineChars(int line, char[] dest) {
        getRegionOnLine(line, 0, getColumnCount(line), dest, 0);
    }

    /**
     * Transform the (line,column) position to index
     * This task will usually be completed by {@link Indexer}
     *
     * @param line   Line of index
     * @param column Column on line of index
     * @return Transformed index for the given arguments
     */
    public int getCharIndex(int line, int column) {
        lock(false);
        try {
            return getIndexer().getCharIndex(line, column);
        } finally {
            unlock(false);
        }
    }

    /**
     * Check if the given {@link CharPosition} is valid in this text. Checks include line, column and index.
     *
     * @param position the position to check, maybe null
     * @return if the position is valid in this text. null position is always invalid.
     */
    public boolean isValidPosition(@Nullable CharPosition position) {
        if (position == null) {
            return false;
        }
        int line = position.line, column = position.column, index = position.index;
        lock(false);
        try {
            if (line < 0 || line >= getLineCount()) {
                return false;
            }
            ContentLine text = getLine(line);
            if (column > text.length() + text.getLineSeparator().getLength() || column < 0) {
                return false;
            }
            return getIndexer().getCharIndex(line, column) == index;
        } finally {
            unlock(false);
        }
    }

    /**
     * Insert content to this object
     *
     * @param line   The insertion's line position
     * @param column The insertion's column position
     * @param text   The text you want to insert at the position
     */
    public void insert(int line, int column, CharSequence text) {
        lock(true);
        documentVersion.getAndIncrement();
        try {
            insertInternal(line, column, text);
        } finally {
            unlock(true);
        }
    }

    private void insertInternal(int line, int column, CharSequence text) {
        checkLineAndColumn(line, column);
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        if (column > lines.get(line).length()) {
            // Never insert texts between line separator characters
            column = lines.get(line).length();
        }

        // Notify listeners and cursor manager
        if (cursor != null)
            cursor.beforeInsert(line, column);

        dispatchBeforeModification();

        int workLine = line;
        int workIndex = column;
        var currLine = makeLineMutable(workLine);
        var helper = InsertTextHelper.forInsertion(text);
        int type, peekType = InsertTextHelper.TYPE_EOF;
        boolean fromPeek = false;
        var newLines = new LinkedList<ContentLine>();
        var startSeparator = currLine.getLineSeparator();
        while (true) {
            type = fromPeek ? peekType : helper.forward();
            fromPeek = false;
            if (type == InsertTextHelper.TYPE_EOF) {
                break;
            }
            if (type == InsertTextHelper.TYPE_LINE_CONTENT) {
                currLine.insert(workIndex, text, helper.getIndex(), helper.getIndexNext());
                workIndex += helper.getIndexNext() - helper.getIndex();
            } else {
                var separator = LineSeparator.fromSeparatorString(text, helper.getIndex(), helper.getIndexNext());
                currLine.setLineSeparator(separator);

                // Peek!
                peekType = helper.forward();
                fromPeek = true;

                var newLine = new ContentLine(currLine.length() - workIndex + helper.getIndexNext() - helper.getIndex() + 10);
                newLine.insert(0, currLine, workIndex, currLine.length());
                currLine.delete(workIndex, currLine.length());
                workIndex = 0;
                // Newly created lines are always mutable
                currLine = newLine;
                newLines.add(newLine);
                workLine++;
            }
        }
        currLine.setLineSeparator(startSeparator);
        lines.addAll(line + 1, newLines);
        helper.recycle();
        textLength += text.length();
        this.dispatchAfterInsert(line, column, workLine, workIndex, text);
    }

    /**
     * Delete character in [start,end)
     *
     * @param start Start position in content
     * @param end   End position in content
     */
    public void delete(int start, int end) {
        lock(true);
        checkIndex(start);
        checkIndex(end);
        documentVersion.getAndIncrement();
        try {
            CharPosition startPos = getIndexer().getCharPosition(start);
            CharPosition endPos = getIndexer().getCharPosition(end);
            if (start != end) {
                deleteInternal(startPos.line, startPos.column, endPos.line, endPos.column);
            }
        } finally {
            unlock(true);
        }
    }

    /**
     * Delete text in the given region
     *
     * @param startLine         The start line position
     * @param columnOnStartLine The start column position
     * @param endLine           The end line position
     * @param columnOnEndLine   The end column position
     */
    public void delete(int startLine, int columnOnStartLine, int endLine, int columnOnEndLine) {
        lock(true);
        documentVersion.getAndIncrement();
        try {
            deleteInternal(startLine, columnOnStartLine, endLine, columnOnEndLine);
        } finally {
            unlock(true);
        }
    }

    private void deleteInternal(int startLine, int columnOnStartLine, int endLine, int columnOnEndLine) {
        checkLineAndColumn(endLine, columnOnEndLine);
        checkLineAndColumn(startLine, columnOnStartLine);
        if (startLine == endLine && columnOnStartLine == columnOnEndLine) {
            return;
        }
        var endLineObj = lines.get(endLine);
        if (columnOnEndLine > endLineObj.length() && endLine + 1 < getLineCount()) {
            // Expected to delete the whole newline
            deleteInternal(startLine, columnOnStartLine, endLine + 1, 0);
            return;
        }
        var startLineObj = lines.get(startLine);
        if (columnOnStartLine > startLineObj.length()) {
            // Expected to delete the whole newline
            deleteInternal(startLine, startLineObj.length(), endLine, columnOnEndLine);
            return;
        }
        var changedContent = new StringBuilder();
        if (startLine == endLine) {
            var curr = makeLineMutable(startLine);
            int len = curr.length();
            if (columnOnStartLine < 0 || columnOnEndLine > len || columnOnStartLine > columnOnEndLine) {
                throw new StringIndexOutOfBoundsException("invalid bounds");
            }

            // Notify listeners and cursor manager
            if (cursor != null) {
                cursor.beforeDelete(startLine, columnOnStartLine, endLine, columnOnEndLine);
            }
            dispatchBeforeModification();

            changedContent.append(curr, columnOnStartLine, columnOnEndLine);
            curr.delete(columnOnStartLine, columnOnEndLine);
            textLength -= columnOnEndLine - columnOnStartLine;
        } else if (startLine < endLine) {
            // Notify listeners and cursor manager
            if (cursor != null)
                cursor.beforeDelete(startLine, columnOnStartLine, endLine, columnOnEndLine);
            dispatchBeforeModification();

            for (int i = startLine + 1; i <= endLine - 1; i++) {
                var line = lines.get(i);
                var separator = lines.get(i).getLineSeparator();
                textLength -= line.length() + separator.getLength();
                line.appendTo(changedContent);
                changedContent.append(separator.getContent());
                line.release();
            }
            if (endLine > startLine + 1) {
                lines.subList(startLine + 1, endLine).clear();
            }

            int currEnd = startLine + 1;
            var start = makeLineMutable(startLine);
            var end = lines.get(currEnd);
            textLength -= start.length() - columnOnStartLine;
            changedContent.insert(0, start, columnOnStartLine, start.length())
                    .insert(start.length() - columnOnStartLine, start.getLineSeparator().getContent());
            start.delete(columnOnStartLine, start.length());
            textLength -= columnOnEndLine;
            changedContent.append(end, 0, columnOnEndLine);
            textLength -= start.getLineSeparator().getLength();
            lines.remove(currEnd);
            start.append(new TextReference(end, columnOnEndLine, end.length()));
            start.setLineSeparator(end.getLineSeparator());
            end.release();
        } else {
            throw new IllegalArgumentException("start line > end line");
        }
        this.dispatchAfterDelete(startLine, columnOnStartLine, endLine, columnOnEndLine, changedContent);
    }

    /**
     * Make the given line mutable
     */
    private ContentLine makeLineMutable(int line) {
        var data = lines.get(line);
        var mut = data.toMutable();
        if (mut != data) {
            lines.set(line, mut);
            data.release();
        }
        return mut;
    }

    /**
     * Replace the text in the given region
     * This action will be completed by calling {@link Content#delete(int, int, int, int)} and {@link Content#insert(int, int, CharSequence)}
     *
     * @param startLine         The start line position
     * @param columnOnStartLine The start column position
     * @param endLine           The end line position
     * @param columnOnEndLine   The end column position
     * @param text              The text to replace old text
     */
    public void replace(int startLine, int columnOnStartLine, int endLine, int columnOnEndLine, CharSequence text) {
        if (text == null) {
            throw new IllegalArgumentException("text can not be null");
        }
        lock(true);
        documentVersion.getAndIncrement();
        try {
            this.dispatchBeforeReplace();
            deleteInternal(startLine, columnOnStartLine, endLine, columnOnEndLine);
            insertInternal(startLine, columnOnStartLine, text);
        } finally {
            unlock(true);
        }
    }

    /**
     * Replace text in the given region with the text
     */
    public void replace(int startIndex, int endIndex, @NonNull CharSequence text) {
        var start = getIndexer().getCharPosition(startIndex);
        var end = getIndexer().getCharPosition(endIndex);
        replace(start.line, start.column, end.line, end.column, text);
    }

    /**
     * Get current document version. The returned value is increasing (if the modification count is
     * smaller than Long.MAX_VALUE).
     */
    public long getDocumentVersion() {
        return documentVersion.get();
    }

    /**
     * Undo the last modification.
     * <p>
     * NOTE: When there are too much modification, old modification will be deleted from UndoManager
     */
    public TextRange undo() {
        return undoManager.undo(this);
    }

    /**
     * Redo the last modification
     */
    public void redo() {
        undoManager.redo(this);
    }

    /**
     * Check whether the {@link UndoManager} is working to undo/redo
     */
    public boolean isUndoManagerWorking() {
        return undoManager.isModifyingContent();
    }

    /**
     * Whether we can undo
     *
     * @return Whether we can undo
     */
    public boolean canUndo() {
        return undoManager.canUndo();
    }

    /**
     * Whether we can redo
     *
     * @return Whether we can redo
     */
    public boolean canRedo() {
        return undoManager.canRedo();
    }

    /**
     * Get whether UndoManager is enabled
     *
     * @return Whether UndoManager is enabled
     */
    public boolean isUndoEnabled() {
        return undoManager.isUndoEnabled();
    }

    /**
     * Set whether enable the UndoManager.
     * If false,any modification will not be taken down and previous modification that
     * is already in UndoManager will be removed.Does not make changes to content.
     *
     * @param enabled New state for UndoManager
     */
    public void setUndoEnabled(boolean enabled) {
        undoManager.setUndoEnabled(enabled);
    }

    /**
     * Get current max stack size of UndoManager
     *
     * @return current max stack size
     */
    public int getMaxUndoStackSize() {
        return undoManager.getMaxUndoStackSize();
    }

    /**
     * Set the max size of stack in UndoManager
     *
     * @param maxSize New max size
     */
    public void setMaxUndoStackSize(int maxSize) {
        undoManager.setMaxUndoStackSize(maxSize);
    }

    /**
     * A delegate method.
     * Notify the UndoManager to begin batch edit(enter a new layer).
     * NOTE: batch edit in Android can be nested.
     *
     * @return Whether in batch edit
     */
    public boolean beginBatchEdit() {
        nestedBatchEdit++;
        return isInBatchEdit();
    }

    /**
     * A delegate method.
     * Notify the UndoManager to end batch edit(exit current layer).
     *
     * @return Whether in batch edit
     */
    public boolean endBatchEdit() {
        nestedBatchEdit--;
        if (nestedBatchEdit == 0) {
            undoManager.onExitBatchEdit();
        }
        if (nestedBatchEdit < 0) {
            nestedBatchEdit = 0;
        }
        return isInBatchEdit();
    }

    public int getNestedBatchEdit() {
        return nestedBatchEdit;
    }

    public void resetBatchEdit() {
        nestedBatchEdit = 0;
    }

    /**
     * Returns whether we are in batch edit
     *
     * @return Whether in batch edit
     */
    public boolean isInBatchEdit() {
        return nestedBatchEdit > 0;
    }

    /**
     * Add a new {@link ContentListener} to the Content
     *
     * @param listener The listener to add
     */
    public void addContentListener(ContentListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener can not be null");
        }
        if (listener instanceof Indexer) {
            throw new IllegalArgumentException("Permission denied");
        }
        if (!contentListeners.contains(listener)) {
            contentListeners.add(listener);
        }
    }

    /**
     * Remove the given listener of this Content
     *
     * @param listener The listener to remove
     */
    public void removeContentListener(ContentListener listener) {
        if (listener instanceof Indexer) {
            throw new IllegalArgumentException("Permission denied");
        }
        contentListeners.remove(listener);
    }

    /**
     * Get the using {@link Indexer} object
     *
     * @return Indexer for this object
     */
    public Indexer getIndexer() {
        if (cursor != null) {
            return cursor.getIndexer();
        }
        return indexer;
    }

    /**
     * Quick method to get sub string of this object
     *
     * @param startLine   The start line position
     * @param startColumn The start column position
     * @param endLine     The end line position
     * @param endColumn   The end column position
     * @return sub-sequence of this Content
     */
    public Content subContent(int startLine, int startColumn, int endLine, int endColumn) {
        lock(false);
        try {
            return subContentInternal(startLine, startColumn, endLine, endColumn);
        } finally {
            unlock(false);
        }
    }

    private Content subContentInternal(int startLine, int startColumn, int endLine, int endColumn) {
        var c = new Content();
        c.setUndoEnabled(false);
        if (startLine == endLine) {
            var line = lines.get(startLine);
            if (endColumn == line.length() + 1 && line.getLineSeparator() == LineSeparator.CRLF) {
                if (startColumn < endColumn) {
                    c.insert(0, 0, line.subSequence(startColumn, line.length()));
                    c.lines.get(0).setLineSeparator(LineSeparator.CR);
                    c.textLength++;
                    c.lines.add(new ContentLine());
                }
            } else {
                c.insert(0, 0, line.subSequence(startColumn, endColumn));
            }
        } else if (startLine < endLine) {
            var firstLine = lines.get(startLine);
            if (firstLine.getLineSeparator() == LineSeparator.CRLF) {
                if (startColumn <= firstLine.length()) {
                    c.insert(0, 0, firstLine.subSequence(startColumn, firstLine.length()));
                    c.lines.get(0).setLineSeparator(firstLine.getLineSeparator());
                    c.textLength += firstLine.getLineSeparator().getLength();
                } else if (startColumn == firstLine.length() + 1) {
                    c.lines.get(0).setLineSeparator(LineSeparator.LF);
                    c.textLength += LineSeparator.LF.getLength();
                } else {
                    throw new IndexOutOfBoundsException();
                }
            } else {
                c.insert(0, 0, firstLine.subSequence(startColumn, firstLine.length()));
                c.lines.get(0).setLineSeparator(firstLine.getLineSeparator());
                c.textLength += firstLine.getLineSeparator().getLength();
            }
            for (int i = startLine + 1; i < endLine; i++) {
                var line = lines.get(i);
                c.lines.add(new ContentLine(line));
                c.textLength += line.length() + line.getLineSeparator().getLength();
            }
            var end = lines.get(endLine);
            if (endColumn == end.length() + 1 && end.getLineSeparator() == LineSeparator.CRLF) {
                var newLine = new ContentLine().insert(0, end, 0, endColumn - 1);
                c.lines.add(newLine);
                newLine.setLineSeparator(LineSeparator.CR);
                c.textLength += endColumn + 1;
            } else {
                c.lines.add(new ContentLine().insert(0, end, 0, endColumn));
                c.textLength += endColumn;
            }
        } else {
            throw new StringIndexOutOfBoundsException("start > end");
        }
        c.setUndoEnabled(true);
        return c;
    }

    private StringBuilder subStringBuilder(int startLine, int startColumn, int endLine, int endColumn, int length) {
        var sb = new StringBuilder(length);
        if (startLine == endLine) {
            var line = lines.get(startLine);
            if (endColumn == line.length() + 1 && line.getLineSeparator() == LineSeparator.CRLF) {
                if (startColumn < endColumn) {
                    sb.append(lines.get(startLine), startColumn, line.length())
                            .append(LineSeparator.CR.getContent());
                }
            } else {
                sb.append(lines.get(startLine), startColumn, endColumn);
            }
        } else if (startLine < endLine) {
            var firstLine = lines.get(startLine);
            if (firstLine.getLineSeparator() == LineSeparator.CRLF) {
                if (startColumn <= firstLine.length()) {
                    sb.append(firstLine, startColumn, firstLine.length());
                    sb.append(firstLine.getLineSeparator().getContent());
                } else if (startColumn == firstLine.length() + 1) {
                    sb.append(LineSeparator.LF.getContent());
                } else {
                    throw new IndexOutOfBoundsException();
                }
            } else {
                sb.append(firstLine, startColumn, firstLine.length());
                sb.append(firstLine.getLineSeparator().getContent());
            }
            for (int i = startLine + 1; i < endLine; i++) {
                var line = lines.get(i);
                sb.append(line)
                        .append(line.getLineSeparator().getContent());
            }
            var end = lines.get(endLine);
            if (endColumn == end.length() + 1 && end.getLineSeparator() == LineSeparator.CRLF) {
                sb.append(end, 0, endColumn)
                        .append(LineSeparator.CR.getContent());
            } else {
                sb.append(end, 0, endColumn);
            }
        } else {
            throw new StringIndexOutOfBoundsException("start > end");
        }
        return sb;
    }

    @NonNull
    public Directions getLineDirections(int line) {
        lock(false);
        try {
            return bidi.getLineDirections(line);
        } finally {
            unlock(false);
        }
    }

    public void setBidiEnabled(boolean enabled) {
        bidi.setEnabled(enabled);
    }

    public boolean isBidiEnabled() {
        return bidi.isEnabled();
    }

    public boolean isRtlAt(int line, int column) {
        var dirs = getLineDirections(line);
        for (int i = 0; i < dirs.getRunCount(); i++) {
            if (column >= dirs.getRunStart(i) && column < dirs.getRunEnd(i)) {
                return dirs.isRunRtl(i);
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object anotherObject) {
        if (anotherObject instanceof Content content) {
            if (content.length() != this.length()) {
                return false;
            }
            for (int i = 0; i < this.getLineCount(); i++) {
                if (!textEquals(lines.get(i), content.lines.get(i))) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(lines, textLength);
    }

    @NonNull
    @Override
    public String toString() {
        return toStringBuilder().toString();
    }

    /**
     * Get the text in StringBuilder form
     * <p>
     * This can improve the speed in char reading for tokenizing
     *
     * @return StringBuilder form of Content
     */
    public StringBuilder toStringBuilder() {
        var sb = new StringBuilder();
        appendToStringBuilder(sb);
        return sb;
    }

    /**
     * Get UndoManager instance in use
     */
    public UndoManager getUndoManager() {
        return undoManager;
    }

    /**
     * Set undo manager. You may use this to recover to a previously saved state of undo stack.
     */
    public void setUndoManager(UndoManager manager) {
        this.undoManager = manager;
    }

    /**
     * Append the content to the given {@link StringBuilder}
     */
    public void appendToStringBuilder(StringBuilder sb) {
        sb.ensureCapacity(sb.length() + length());
        lock(false);
        try {
            final int lines = getLineCount();
            for (int i = 0; i < lines; i++) {
                var line = this.lines.get(i);
                line.appendTo(sb);
                sb.append(line.getLineSeparator().getContent());
            }
        } finally {
            unlock(false);
        }
    }

    /**
     * Get Cursor for editor (Create if there is not)
     *
     * @return Cursor
     */
    public Cursor getCursor() {
        if (cursor == null) {
            cursor = new Cursor(this);
        }
        return cursor;
    }

    /**
     * Check if there is a cursor created for this Content object
     */
    public boolean isCursorCreated() {
        return cursor != null;
    }

    /**
     * Dispatch events to listener before replacement
     */
    private void dispatchBeforeReplace() {
        undoManager.beforeReplace(this);
        if (cursor != null)
            cursor.beforeReplace();
        if (indexer instanceof ContentListener) {
            ((ContentListener) indexer).beforeReplace(this);
        }
        for (ContentListener lis : contentListeners) {
            lis.beforeReplace(this);
        }
    }

    /**
     * Dispatch events to listener after deletion
     *
     * @param a Start line
     * @param b Start Column
     * @param c End line
     * @param d End column
     * @param e Text deleted
     */
    private void dispatchAfterDelete(int a, int b, int c, int d, @NonNull CharSequence e) {
        undoManager.afterDelete(this, a, b, c, d, e);
        if (cursor != null)
            cursor.afterDelete(a, b, c, d, e);
        if (indexer instanceof ContentListener) {
            ((ContentListener) indexer).afterDelete(this, a, b, c, d, e);
        }
        for (ContentListener lis : contentListeners) {
            lis.afterDelete(this, a, b, c, d, e);
        }
    }

    private void dispatchBeforeModification() {
        undoManager.beforeModification(this);
        for (ContentListener lis : contentListeners) {
            lis.beforeModification(this);
        }
    }

    /**
     * Dispatch events to listener after insertion
     *
     * @param a Start line
     * @param b Start Column
     * @param c End line
     * @param d End column
     * @param e Text deleted
     */
    private void dispatchAfterInsert(int a, int b, int c, int d, @NonNull CharSequence e) {
        undoManager.afterInsert(this, a, b, c, d, e);
        if (cursor != null)
            cursor.afterInsert(a, b, c, d, e);
        if (indexer instanceof ContentListener) {
            ((ContentListener) indexer).afterInsert(this, a, b, c, d, e);
        }
        for (ContentListener lis : contentListeners) {
            lis.afterInsert(this, a, b, c, d, e);
        }
    }

    /**
     * Check whether the index is valid
     *
     * @param index Index to check
     */
    protected void checkIndex(int index) {
        if (index > length() || index < 0) {
            throw new StringIndexOutOfBoundsException("Index " + index + " out of bounds. length:" + length());
        }
    }

    /**
     * Check whether the line is valid
     *
     * @param line Line to check
     */
    protected void checkLine(int line) {
        if (line >= getLineCount() || line < 0) {
            throw new StringIndexOutOfBoundsException("Line " + line + " out of bounds. line count:" + getLineCount());
        }
    }

    /**
     * Check whether the line and column is valid
     *
     * @param line   The line to check
     * @param column The column to check
     */
    protected void checkLineAndColumn(int line, int column) {
        checkLine(line);
        var text = lines.get(line);
        int len = text.length() + text.getLineSeparator().getLength();
        if (column > len || column < 0) {
            throw new StringIndexOutOfBoundsException(
                    "Column " + column + " out of bounds. line: " + line + " , column count (line separator included):" + len);
        }
    }

    /**
     * Copy text in this Content object.
     * Returns a new thread-safe Content object with the same text as this object. By default, the object is
     * thread-safe and access operations are locked when accessed by multiple threads.
     */
    public Content copyText() {
        return copyText(true);
    }

    /**
     * Copy text in this Content object.
     * Returns a new Content object with the same text as this object.
     */
    public Content copyText(boolean newContentThreadSafe) {
        return copyText(newContentThreadSafe, false);
    }

    /**
     * Copy text in this Content object.
     * Returns a new Content object with the same text as this object.
     */
    public Content copyText(boolean newContentThreadSafe, boolean shallow) {
        lock(false);
        try {
            var n = new Content(null, newContentThreadSafe);
            n.lines.remove(0);
            ((ArrayList<ContentLine>) n.lines).ensureCapacity(getLineCount());
            if (shallow) {
                for (ContentLine line : lines) {
                    line.retain();
                }
                n.lines.addAll(lines);
            } else {
                for (ContentLine line : lines) {
                    n.lines.add(new ContentLine(line));
                }
            }
            n.textLength = textLength;
            return n;
        } finally {
            unlock(false);
        }
    }

    /**
     * Shallow copy text in this Content object.
     * Returns a new Content object with the same text as this object. By default, the object is not
     * thread-safe and should be accessed by a single thread.
     */
    public Content copyTextShallow() {
        return copyTextShallow(false);
    }

    /**
     * Shallow copy text in this Content object.
     * Returns a new Content object with the same text as this object.
     */
    public Content copyTextShallow(boolean newContentThreadSafe) {
        return copyText(newContentThreadSafe, true);
    }

    /**
     * Release this text object.
     * Release any shareable instance currently held. It's recommended to call this after a shallow copied
     * instance is no longer used.
     */
    public void release() {
        lock(true);
        try {
            for (ContentLine line : lines) {
                line.release();
            }
            lines.clear();
            textLength = 0;
            this.cursor = null;
            this.bidi.destroy();
        } finally {
            unlock(true);
        }
    }

    protected int getColumnCountUnsafe(int line) {
        return lines.get(line).length();
    }

    @NonNull
    protected LineSeparator getLineSeparatorUnsafe(int line) {
        return lines.get(line).getLineSeparator();
    }

    /**
     * Read the lines (ordered).
     * This is for optimizing frequent lock acquiring.
     *
     * @param startLine inclusive
     * @param endLine   inclusive
     */
    public void runReadActionsOnLines(int startLine, int endLine, @NonNull ContentLineConsumer consumer) {
        lock(false);
        try {
            for (int i = startLine; i <= endLine; i++) {
                consumer.accept(i, lines.get(i), bidi.getLineDirections(i));
            }
        } finally {
            unlock(false);
        }
    }

    /**
     * Read the lines (ordered).
     * This is for optimizing frequent lock acquiring.
     *
     * @param startLine inclusive
     * @param endLine   inclusive
     */
    public void runReadActionsOnLines(int startLine, int endLine, @NonNull ContentLineConsumer2 consumer) {
        lock(false);
        try {
            var flag = new ContentLineConsumer2.AbortFlag();
            for (int i = startLine; i <= endLine && !flag.set; i++) {
                consumer.accept(i, lines.get(i), flag);
            }
        } finally {
            unlock(false);
        }
    }


    public interface ContentLineConsumer {

        void accept(int lineIndex, @NonNull ContentLine line, @NonNull Directions dirs);

    }

    public interface ContentLineConsumer2 {

        void accept(int lineIndex, @NonNull ContentLine line, @NonNull AbortFlag flag);

        class AbortFlag {
            public boolean set = false;
        }

    }
}
