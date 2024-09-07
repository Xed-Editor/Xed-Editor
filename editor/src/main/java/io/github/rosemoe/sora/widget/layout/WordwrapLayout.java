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
package io.github.rosemoe.sora.widget.layout;

import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import io.github.rosemoe.sora.graphics.CharPosDesc;
import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.util.MyCharacter;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Wordwrap layout for editor
 * <p>
 * This layout will not let character displayed outside the editor's width
 * <p>
 * However, using this can be power-costing because we will have to recreate this layout in various
 * conditions, such as when the line number increases and its width grows or when the text size has changed
 *
 * @author Rose
 */
public class WordwrapLayout extends AbstractLayout {

    private final int width;
    private final boolean antiWordBreaking;
    private List<RowRegion> rowTable;

    public WordwrapLayout(CodeEditor editor, Content text, boolean antiWordBreaking, List<RowRegion> extended, boolean clearCache) {
        super(editor, text);
        this.antiWordBreaking = antiWordBreaking;
        rowTable = extended != null ? extended : new ArrayList<>();
        if (clearCache) {
            rowTable.clear();
        }
        width = editor.getWidth() - (int) (editor.measureTextRegionOffset() + editor.getTextPaint().measureText("a"));
        breakAllLines();
    }

    public List<RowRegion> getRowTable() {
        return rowTable;
    }

    private void breakAllLines() {
        var taskCount = Math.min(SUBTASK_COUNT, (int) Math.ceil((float) text.getLineCount() / MIN_LINE_COUNT_FOR_SUBTASK));
        var sizeEachTask = text.getLineCount() / taskCount;
        var monitor = new TaskMonitor(taskCount,(results, cancelledCount) -> {
            final var editor = this.editor;
            if (editor != null) {
                List<WordwrapResult> r2 = new ArrayList<>();
                for (Object result : results) {
                    r2.add((WordwrapResult) result);
                }
                Collections.sort(r2);
                editor.postInLifecycle(() -> {
                    if (WordwrapLayout.this.editor != editor) {
                        // This layout could have been abandoned when waiting for Runnable execution
                        // See #307
                        return;
                    }
                    if (rowTable != null) {
                        rowTable.clear();
                    } else {
                        rowTable = new ArrayList<>();
                    }
                    for (WordwrapResult wordwrapResult : r2) {
                        rowTable.addAll(wordwrapResult.regions);
                    }
                    editor.setLayoutBusy(false);
                    editor.getEventHandler().scrollBy(0, 0);
                });
            }
        });
        for (int i = 0; i < taskCount; i++) {
            var start = sizeEachTask * i;
            var end = i + 1 == taskCount ? (text.getLineCount() - 1) : (sizeEachTask * (i + 1) - 1);
            submitTask(new WordwrapAnalyzeTask(monitor, i, start, end));
        }
    }

    private int findRow(int line) {
        int index;
        // Binary find line
        int left = 0, right = rowTable.size();
        while (left <= right) {
            var mid = (left + right) / 2;
            if (mid < 0 || mid >= rowTable.size()) {
                left = Math.max(0, Math.min(rowTable.size() - 1, mid));
                break;
            }
            int value = rowTable.get(mid).line;
            if (value < line) {
                left = mid + 1;
            } else if (value > line) {
                right = mid - 1;
            } else {
                left = mid;
                break;
            }
        }
        index = left;
        while (index > 0 && rowTable.get(index).startColumn > 0) {
            index--;
        }
        return index;
    }

    public int findRow(int line, int column) {
        int row = findRow(line);
        while (rowTable.get(row).endColumn <= column && row + 1 < rowTable.size() && rowTable.get(row + 1).line == line) {
            row++;
        }
        return row;
    }

    private void breakLines(int startLine, int endLine) {
        int insertPosition = 0;
        while (insertPosition < rowTable.size()) {
            if (rowTable.get(insertPosition).line < startLine) {
                insertPosition++;
            } else {
                break;
            }
        }
        while (insertPosition < rowTable.size()) {
            int line = rowTable.get(insertPosition).line;
            if (line >= startLine && line <= endLine) {
                rowTable.remove(insertPosition);
            } else {
                break;
            }
        }
        List<Integer> breakpoints = new ArrayList<>();
        List<RowRegion> newRegions = new ArrayList<>();
        for (int i = startLine; i <= endLine; i++) {
            breakLine(i, text.getLine(i), breakpoints, null);
            for (int j = -1; j < breakpoints.size(); j++) {
                int start = j == -1 ? 0 : breakpoints.get(j);
                int end = j + 1 < breakpoints.size() ? breakpoints.get(j + 1) : text.getColumnCount(i);
                newRegions.add(new RowRegion(i, start, end));
            }
            breakpoints.clear();
        }
        rowTable.addAll(insertPosition, newRegions);
    }

    private void breakLine(int line, ContentLine sequence, List<Integer> breakpoints, @Nullable Paint paint) {
        int start = 0;
        int len = sequence.length();
        var text = sequence.getBackingCharArray();

        while (start < len) {
            var next = CharPosDesc.getTextOffset(editor.getRenderer().findFirstVisibleCharForWordwrap(width, line, start, len, 0, paint == null ? editor.getTextPaint() : paint));
            // Force to break the text, though no space is available
            if (next == start) {
                next++;
            }
            if (antiWordBreaking && MyCharacter.isAlpha(text[next - 1]) && next < len && (MyCharacter.isAlpha(text[next]) || text[next] == '-')) {
                int wordStart = next - 1;
                while (wordStart > start && MyCharacter.isAlpha(text[wordStart - 1])) {
                    wordStart--;
                }
                if (wordStart > start) {
                    next = wordStart;
                }
            }
            breakpoints.add(next);
            start = next;
        }
        if (!breakpoints.isEmpty() && breakpoints.get(breakpoints.size() - 1) == sequence.length()) {
            breakpoints.remove(breakpoints.size() - 1);
        }
    }

    @Override
    public void beforeReplace(@NonNull Content content) {
        // Intentionally empty
    }

    @Override
    public void afterInsert(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence insertedContent) {
        super.afterInsert(content, startLine, startColumn, endLine, endColumn, insertedContent);
        // Update line numbers
        int delta = endLine - startLine;
        if (delta != 0) {
            for (int row = findRow(startLine + 1); row < rowTable.size(); row++) {
                rowTable.get(row).line += delta;
            }
        }
        // Re-break
        breakLines(startLine, endLine);
    }

    @Override
    public void afterDelete(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence deletedContent) {
        super.afterDelete(content, startLine, startColumn, endLine, endColumn, deletedContent);
        int delta = endLine - startLine;
        if (delta != 0) {
            int startRow = findRow(startLine);
            while (startRow < rowTable.size()) {
                int line = rowTable.get(startRow).line;
                if (line >= startLine && line <= endLine) {
                    rowTable.remove(startRow);
                } else {
                    break;
                }
            }
            for (int row = findRow(endLine + 1); row < rowTable.size(); row++) {
                var region = rowTable.get(row);
                if (region.line >= endLine)
                    region.line -= delta;
            }
        }
        breakLines(startLine, startLine);
    }

    @Override
    public void destroyLayout() {
        super.destroyLayout();
        rowTable = null;
    }

    @NonNull
    @Override
    public Row getRowAt(int rowIndex) {
        if (rowTable.isEmpty()) {
            var r = new Row();
            r.startColumn = 0;
            r.endColumn = text.getColumnCount(rowIndex);
            r.isLeadingRow = true;
            r.lineIndex = rowIndex;
            return r;
        }
        return rowTable.get(rowIndex).toRow();
    }

    @Override
    public int getLineNumberForRow(int row) {
        if (rowTable.isEmpty()) {
            return Math.max(0, Math.min(row, text.getLineCount() - 1));
        }
        return row >= rowTable.size() ? rowTable.get(rowTable.size() - 1).line : rowTable.get(row).line;
    }

    @NonNull
    @Override
    public RowIterator obtainRowIterator(int initialRow, @Nullable SparseArray<ContentLine> preloadedLines) {
        return rowTable.isEmpty() ? new LineBreakLayout.LineBreakLayoutRowItr(text, initialRow, preloadedLines) : new WordwrapLayoutRowItr(initialRow);
    }

    @Override
    public long getUpPosition(int line, int column) {
        if (rowTable.isEmpty()) {
            if (line - 1 < 0) {
                return IntPair.pack(0, 0);
            }
            int c_column = text.getColumnCount(line - 1);
            if (column > c_column) {
                column = c_column;
            }
            return IntPair.pack(line - 1, column);
        }
        int row = findRow(line, column);
        if (row > 0) {
            var offset = column - rowTable.get(row).startColumn;
            var lastRow = rowTable.get(row - 1);
            var max = lastRow.endColumn - lastRow.startColumn;
            offset = Math.min(offset, max);
            return IntPair.pack(lastRow.line, lastRow.startColumn + offset);
        }
        return IntPair.pack(0, 0);
    }

    @Override
    public long getDownPosition(int line, int column) {
        if (rowTable.isEmpty()) {
            int c_line = text.getLineCount();
            if (line + 1 >= c_line) {
                return IntPair.pack(line, text.getColumnCount(line));
            } else {
                int c_column = text.getColumnCount(line + 1);
                if (column > c_column) {
                    column = c_column;
                }
                return IntPair.pack(line + 1, column);
            }
        }
        int row = findRow(line, column);
        if (row + 1 < rowTable.size()) {
            var offset = column - rowTable.get(row).startColumn;
            var nextRow = rowTable.get(row + 1);
            var max = nextRow.endColumn - nextRow.startColumn;
            offset = Math.min(offset, max);
            return IntPair.pack(nextRow.line, nextRow.startColumn + offset);
        } else {
            return IntPair.pack(line, text.getColumnCount(line));
        }
    }

    @Override
    public int getLayoutWidth() {
        return 0;
    }

    @Override
    public int getLayoutHeight() {
        if (rowTable.isEmpty()) {
            return editor.getRowHeight() * text.getLineCount();
        }
        return rowTable.size() * editor.getRowHeight();
    }

    @Override
    public int getRowIndexForPosition(int index) {
        var pos = editor.getText().getIndexer().getCharPosition(index);
        var line = pos.line;
        if (rowTable.isEmpty()) {
            return line;
        }
        var column = pos.column;
        int row = findRow(line);
        if (row < rowTable.size()) {
            var region = rowTable.get(row);
            if (region.line != line) {
                return 0;
            }
            while (region.startColumn < column && row + 1 < rowTable.size()) {
                row++;
                region = rowTable.get(row);
                if (region.line != line || region.startColumn > column) {
                    row--;
                    break;
                }
            }
            return row;
        }
        return 0;
    }

    @Override
    public long getCharPositionForLayoutOffset(float xOffset, float yOffset) {
        if (rowTable.isEmpty()) {
            int lineCount = text.getLineCount();
            int line = Math.min(lineCount - 1, Math.max((int) (yOffset / editor.getRowHeight()), 0));
            int res = BidiLayout.horizontalIndex(editor, this, text, line, 0, text.getColumnCount(line), xOffset);
            return IntPair.pack(line, res);
        }
        int row = (int) (yOffset / editor.getRowHeight());
        row = Math.max(0, Math.min(row, rowTable.size() - 1));
        RowRegion region = rowTable.get(row);
        int column = BidiLayout.horizontalIndex(editor, this, text, region.line, region.startColumn, region.endColumn, xOffset);
        return IntPair.pack(region.line, column);
    }

    @NonNull
    @Override
    public float[] getCharLayoutOffset(int line, int column, float[] dest) {
        if (dest == null || dest.length < 2) {
            dest = new float[2];
        }
        if (rowTable.isEmpty()) {
            dest[0] = editor.getRowBottom(line);
            dest[1] = BidiLayout.horizontalOffset(editor, this, text, line, 0, text.getColumnCount(line), column);
            return dest;
        }
        int row = findRow(line);
        if (row < rowTable.size()) {
            RowRegion region = rowTable.get(row);
            if (region.line != line) {
                dest[0] = dest[1] = 0;
                return dest;
            }
            while (region.startColumn < column && row + 1 < rowTable.size()) {
                row++;
                region = rowTable.get(row);
                if (region.line != line || region.startColumn > column) {
                    row--;
                    region = rowTable.get(row);
                    break;
                }
            }
            dest[0] = editor.getRowBottom(row);
            dest[1] = BidiLayout.horizontalOffset(editor, this, text, region.line, region.startColumn, region.endColumn, column);
        } else {
            dest[0] = dest[1] = 0;
        }
        return dest;
    }

    @Override
    public int getRowCountForLine(int line) {
        if (rowTable.isEmpty()) {
            return 1;
        }
        int row = findRow(line);
        int count = 0;
        while (row < rowTable.size() && rowTable.get(row).line == line) {
            count++;
            row++;
        }
        return count;
    }

    /**
     * Get soft breaks on the given line
     */
    public List<Integer> getSoftBreaksForLine(int line) {
        if (rowTable.isEmpty()) {
            return Collections.emptyList();
        }
        int row = findRow(line);
        var list = new ArrayList<Integer>();
        while (row < rowTable.size() && rowTable.get(row).line == line) {
            var column = rowTable.get(row).startColumn;
            if (column != 0) {
                list.add(column);
            }
            row++;
        }
        return list;
    }

    @Override
    public int getRowCount() {
        if (rowTable.isEmpty()) {
            return text.getLineCount();
        }
        return rowTable.size();
    }

    static class RowRegion {

        final int startColumn;
        final int endColumn;
        int line;

        RowRegion(int line, int start, int end) {
            this.line = line;
            startColumn = start;
            endColumn = end;
        }

        public Row toRow() {
            var row = new Row();
            row.isLeadingRow = startColumn == 0;
            row.startColumn = startColumn;
            row.endColumn = endColumn;
            row.lineIndex = line;
            return row;
        }

        @NonNull
        @Override
        public String toString() {
            return "RowRegion{" +
                    "startColumn=" + startColumn +
                    ", endColumn=" + endColumn +
                    ", line=" + line +
                    '}';
        }
    }

    private static class WordwrapResult implements Comparable<WordwrapResult> {

        int index;
        List<RowRegion> regions;

        public WordwrapResult(int idx, List<RowRegion> r) {
            index = idx;
            regions = r;
        }

        @Override
        public int compareTo(WordwrapResult wordwrapResult) {
            return Integer.compare(index, wordwrapResult.index);
        }
    }

    class WordwrapLayoutRowItr implements RowIterator {

        private final Row result;
        private final int initRow;
        private int currentRow;

        WordwrapLayoutRowItr(int initialRow) {
            initRow = currentRow = initialRow;
            result = new Row();
        }

        @NonNull
        @Override
        public Row next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            RowRegion region = rowTable.get(currentRow);
            result.lineIndex = region.line;
            result.startColumn = region.startColumn;
            result.endColumn = region.endColumn;
            result.isLeadingRow = currentRow <= 0 || rowTable.get(currentRow - 1).line != region.line;
            currentRow++;
            return result;
        }

        @Override
        public boolean hasNext() {
            return currentRow >= 0 && currentRow < rowTable.size();
        }

        @Override
        public void reset() {
            currentRow = initRow;
        }
    }

    private class WordwrapAnalyzeTask extends LayoutTask<WordwrapResult> {

        private final int start, end, id;
        private final Paint paint;

        WordwrapAnalyzeTask(TaskMonitor monitor, int id, int start, int end) {
            super(monitor);
            this.start = start;
            this.id = id;
            this.end = end;
            paint = new Paint(editor.isRenderFunctionCharacters());
            paint.set(editor.getTextPaint());
            paint.onAttributeUpdate();
        }

        @Override
        protected WordwrapResult compute() {
            editor.setLayoutBusy(true);
            var list = new ArrayList<RowRegion>();
            var breakpoints = new ArrayList<Integer>();
            text.runReadActionsOnLines(start, end, (int index, ContentLine line, Content.ContentLineConsumer2.AbortFlag abortFlag) -> {
                breakLine(index, line, breakpoints, paint);
                for (int j = -1; j < breakpoints.size(); j++) {
                    int start = j == -1 ? 0 : breakpoints.get(j);
                    int end = j + 1 < breakpoints.size() ? breakpoints.get(j + 1) : line.length();
                    list.add(new RowRegion(index, start, end));
                }
                if (!shouldRun()) {
                    abortFlag.set = true;
                }
                breakpoints.clear();
            });
            return new WordwrapResult(id, list);
        }
    }

}
