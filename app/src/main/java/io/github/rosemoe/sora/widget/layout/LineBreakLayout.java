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

import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.rosemoe.sora.graphics.Paint;
import io.github.rosemoe.sora.graphics.SingleCharacterWidths;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.util.BlockIntList;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Layout implementation of editor
 * This layout is never broke unless there is actually a newline character
 *
 * @author Rose
 */
public class LineBreakLayout extends AbstractLayout {

    private final AtomicInteger reuseCount = new AtomicInteger(0);
    private BlockIntList widthMaintainer;
    private SingleCharacterWidths measurer;

    public LineBreakLayout(CodeEditor editor, Content text) {
        super(editor, text);
        measurer = new SingleCharacterWidths(editor.getTabWidth());
        measurer.setHandleFunctionCharacters(editor.isRenderFunctionCharacters());
        widthMaintainer = new BlockIntList();
        measureAllLines(widthMaintainer);
    }

    private void measureAllLines(BlockIntList widthMaintainer) {
        if (text == null) {
            return;
        }
        var shadowPaint = new Paint(editor.isRenderFunctionCharacters());
        shadowPaint.set(editor.getTextPaint());
        shadowPaint.onAttributeUpdate();
        var reuseCountLocal = reuseCount.get();
        var measurerLocal = measurer;
        final var monitor = new TaskMonitor(1, (results, cancelledCount) -> {
            final var editor = this.editor;
            if (editor == null || cancelledCount > 0) {
                return;
            }
            editor.postInLifecycle(() -> {
                if (LineBreakLayout.this.editor != editor || reuseCountLocal != reuseCount.get()) {
                    // This layout could have been abandoned when waiting for Runnable execution
                    // See #307
                    return;
                }
                editor.setLayoutBusy(false);
                editor.getEventHandler().scrollBy(0, 0);
            });
        });
        var task = new LayoutTask<Void>(monitor) {
            @Override
            protected Void compute() {
                widthMaintainer.lock.lock();
                try {
                    editor.setLayoutBusy(true);
                    text.runReadActionsOnLines(0, text.getLineCount() - 1, (int index, ContentLine line, Content.ContentLineConsumer2.AbortFlag abortFlag) -> {
                        var width = (int) measurerLocal.measureText(line, 0, line.length(), shadowPaint);
                        if (shouldRun()) {
                            widthMaintainer.add(width);
                        } else {
                            abortFlag.set = true;
                        }
                    });
                } finally {
                    widthMaintainer.lock.unlock();
                }
                return null;
            }

            @Override
            protected boolean shouldRun() {
                return super.shouldRun() && reuseCount.get() == reuseCountLocal;
            }
        };
        submitTask(task);
    }

    private int measureLine(int lineIndex) {
        ContentLine line = text.getLine(lineIndex);
        return (int) measurer.measureText(line, 0, line.length(), editor.getTextPaint());
    }

    private int measureRegion(int lineIndex, int start, int end) {
        ContentLine line = text.getLine(lineIndex);
        return (int) measurer.measureText(line, start, end, editor.getTextPaint());
    }

    @NonNull
    @Override
    public RowIterator obtainRowIterator(int initialRow, @Nullable SparseArray<ContentLine> preloadedLines) {
        return new LineBreakLayoutRowItr(text, initialRow, preloadedLines);
    }

    @Override
    public int getRowCount() {
        return text.getLineCount();
    }

    @Override
    public void beforeReplace(@NonNull Content content) {
        // Intentionally empty
    }

    @Override
    public void afterInsert(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence insertedContent) {
        super.afterInsert(content, startLine, startColumn, endLine, endColumn, insertedContent);
        for (int i = startLine; i <= endLine; i++) {
            if (i == startLine) {
                if (endLine == startLine) {
                    widthMaintainer.set(i, widthMaintainer.get(i) + measureRegion(i, startColumn, endColumn));
                } else {
                    widthMaintainer.set(i, measureLine(i));
                }
            } else {
                widthMaintainer.add(i, measureLine(i));
            }
        }
    }

    @Override
    public void afterDelete(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence deletedContent) {
        super.afterDelete(content, startLine, startColumn, endLine, endColumn, deletedContent);
        if (startLine < endLine) {
            widthMaintainer.removeRange(startLine + 1, endLine + 1);
        }
        if (startLine == endLine) {
            widthMaintainer.set(startLine, widthMaintainer.get(startLine) - (int) measurer.measureText(deletedContent, 0, endColumn - startColumn, editor.getTextPaint()));
        } else {
            widthMaintainer.set(startLine, measureLine(startLine));
        }
    }

    @NonNull
    @Override
    public Row getRowAt(int rowIndex) {
        var row = new Row();
        row.lineIndex = rowIndex;
        row.startColumn = 0;
        row.isLeadingRow = true;
        row.endColumn = text.getColumnCount(rowIndex);
        return row;
    }

    @Override
    public int getRowIndexForPosition(int index) {
        return editor.getText().getIndexer().getCharPosition(index).line;
    }

    @Override
    public void destroyLayout() {
        super.destroyLayout();
        widthMaintainer = null;
    }

    @Override
    public int getLineNumberForRow(int row) {
        return Math.max(0, Math.min(row, text.getLineCount() - 1));
    }

    @Override
    public int getLayoutWidth() {
        return widthMaintainer.size() == 0 ? Integer.MAX_VALUE / 10 : widthMaintainer.getMax();
    }

    @Override
    public int getLayoutHeight() {
        return text.getLineCount() * editor.getRowHeight();
    }

    @Override
    public long getCharPositionForLayoutOffset(float xOffset, float yOffset) {
        int lineCount = text.getLineCount();
        int line = Math.min(lineCount - 1, Math.max((int) (yOffset / editor.getRowHeight()), 0));
        int res = BidiLayout.horizontalIndex(editor, this, text, line, 0, text.getColumnCount(line), xOffset);
        return IntPair.pack(line, res);
    }

    @NonNull
    @Override
    public float[] getCharLayoutOffset(int line, int column, float[] dest) {
        if (dest == null || dest.length < 2) {
            dest = new float[2];
        }
        dest[0] = editor.getRowBottom(line);
        dest[1] = BidiLayout.horizontalOffset(editor, this, text, line, 0, text.getColumnCount(line), column);
        return dest;
    }

    @Override
    public int getRowCountForLine(int line) {
        return 1;
    }

    @Override
    public long getDownPosition(int line, int column) {
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

    @Override
    public long getUpPosition(int line, int column) {
        if (line - 1 < 0) {
            return IntPair.pack(0, 0);
        }
        int c_column = text.getColumnCount(line - 1);
        if (column > c_column) {
            column = c_column;
        }
        return IntPair.pack(line - 1, column);
    }

    public void reuse(Content text) {
        this.text = text;
        reuseCount.getAndIncrement();
        measurer = new SingleCharacterWidths(editor.getTabWidth());
        measurer.setHandleFunctionCharacters(editor.isRenderFunctionCharacters());
        try {
            if (widthMaintainer.lock.tryLock(5, TimeUnit.MILLISECONDS)) {
                widthMaintainer.lock.unlock();
                widthMaintainer.clear();
                measureAllLines(widthMaintainer);
            } else {
                measureAllLines(widthMaintainer = new BlockIntList());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to wait for lock", e);
        }
    }

    static class LineBreakLayoutRowItr implements RowIterator {

        private final Content text;
        private final Row result;
        private final int initRow;
        private final SparseArray<ContentLine> preloadedLines;
        private int currentRow;

        LineBreakLayoutRowItr(@NonNull Content text, int initialRow, @Nullable SparseArray<ContentLine> preloadedLines) {
            initRow = currentRow = initialRow;
            result = new Row();
            this.text = text;
            result.isLeadingRow = true;
            result.startColumn = 0;
            this.preloadedLines = preloadedLines;
        }

        @NonNull
        @Override
        public Row next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            result.lineIndex = currentRow;
            var line = preloadedLines != null ? preloadedLines.get(currentRow) : null;
            if (line == null) {
                line = text.getLine(currentRow);
            }
            result.endColumn = line.length();
            currentRow++;
            return result;
        }

        @Override
        public boolean hasNext() {
            return currentRow >= 0 && currentRow < text.getLineCount();
        }

        @Override
        public void reset() {
            currentRow = initRow;
        }
    }

}
