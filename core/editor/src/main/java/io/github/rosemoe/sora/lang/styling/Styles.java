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
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.github.rosemoe.sora.data.ObjectAllocator;
import io.github.rosemoe.sora.lang.styling.line.LineAnchorStyle;
import io.github.rosemoe.sora.lang.styling.line.LineStyles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.util.MutableInt;

/**
 * This class stores styles of text and other decorations in editor related to code.
 * <p>
 * Note that this does not save any information related to languages. No extra space is provided
 * for communication between analyzers and auto-completion. You should manage it by yourself.
 * <p>
 * If you are going to extend this class, please read the source code carefully in advance
 * </p>
 */
@SuppressWarnings("unused")
public class Styles {

    public Spans spans;

    /**
     * <strong>Sorted</strong> list of LineStyles
     */
    public List<LineStyles> lineStyles;
    public Map<Class<?>, MutableInt> styleTypeCount;

    public List<CodeBlock> blocks;
    /**
     * Internal, automatically generated
     */
    public List<CodeBlock> blocksByStart;

    public int suppressSwitch = Integer.MAX_VALUE;

    public boolean indentCountMode = false;

    public Styles() {
        this(null);
    }

    public Styles(@Nullable Spans spans) {
        this(spans, true);
    }

    public Styles(@Nullable Spans spans, boolean initCodeBlocks) {
        this.spans = spans;
        if (initCodeBlocks) {
            blocks = new ArrayList<>(128);
        }
    }

    /**
     * Get analyzed spans
     */
    @Nullable
    public Spans getSpans() {
        return spans;
    }

    /**
     * Get a new BlockLine object
     *
     * @return An idle BlockLine
     */
    @NonNull
    public CodeBlock obtainNewBlock() {
        return ObjectAllocator.obtainBlockLine();
    }

    /**
     * Add a new code block info
     *
     * @param block Info of code block
     */
    public void addCodeBlock(@NonNull CodeBlock block) {
        Objects.requireNonNull(block, "CodeBlock must not be null");
        blocks.add(block);
    }

    /**
     * Returns suppress switch
     *
     * @return suppress switch
     * @see Styles#setSuppressSwitch(int)
     */
    public int getSuppressSwitch() {
        return suppressSwitch;
    }

    /**
     * Set suppress switch for editor
     * What is 'suppress switch' ?:
     * <p>
     * Suppress switch is a switch size for code block line drawing
     * and for the process to find out which code block the cursor is in.
     * Because the code blocks are not saved by the order of both start line and
     * end line,we are unable to know exactly when we should stop the process.
     * So without a suppressing switch,it will cost a large of time to search code
     * blocks.
     * <p>
     * A suppressing switch is the code block count in the first layer code block
     * (as well as its sub code blocks).
     * If you are unsure,do not set it.
     * <p>
     * The default value is Integer.MAX_VALUE
     */
    public void setSuppressSwitch(int suppressSwitch) {
        this.suppressSwitch = suppressSwitch;
    }

    /**
     * Adjust styles on insert.
     */
    public void adjustOnInsert(@NonNull CharPosition start, @NonNull CharPosition end) {
        spans.adjustOnInsert(start, end);
        var delta = end.line - start.line;
        if (delta == 0) {
            return;
        }
        if (blocks != null)
            BlocksUpdater.update(blocks, start.line, delta);
        if (lineStyles != null) {
            for (var styles : lineStyles) {
                if (styles.getLine() > start.line) {
                    styles.setLine(styles.getLine() + delta);
                    styles.updateElements();
                }
            }
        }
    }

    /**
     * Adjust styles on delete.
     */
    public void adjustOnDelete(@NonNull CharPosition start, @NonNull CharPosition end) {
        spans.adjustOnDelete(start, end);
        var delta = start.line - end.line;
        if (delta == 0) {
            return;
        }
        if (blocks != null)
            BlocksUpdater.update(blocks, start.line, delta);
        if (lineStyles != null) {
            var itr = lineStyles.iterator();
            while (itr.hasNext()) {
                var styles = itr.next();
                var line = styles.getLine();
                if (line > end.line) {
                    styles.setLine(line + delta);
                    styles.updateElements();
                } else if (line > start.line /* line <= end.line */) {
                    itr.remove();
                }
            }
        }
    }

    public void addLineStyle(@NonNull LineAnchorStyle style) {
        if (lineStyles == null) {
            lineStyles = new ArrayList<>();
            styleTypeCount = new ConcurrentHashMap<>();
        }
        var type = style.getClass();
        for (var lineStyle : lineStyles) {
            if (lineStyle.getLine() == style.getLine()) {
                styleCountUpdate(type, lineStyle.addStyle(style));
                return;
            }
        }
        var lineStyle = new LineStyles(style.getLine());
        lineStyles.add(lineStyle);
        styleCountUpdate(type, lineStyle.addStyle(style));
    }

    private void styleCountUpdate(@NonNull Class<?> type, int delta) {
        var res = styleTypeCount.get(type);
        if (res == null) {
            res = new MutableInt(0);
            styleTypeCount.put(type, res);
        }
        res.value += delta;
    }

    /**
     * Remove the style of given kind from line
     */
    public void eraseLineStyle(int line, @NonNull Class<? extends LineAnchorStyle> type) {
        if (lineStyles == null) {
            return;
        }
        for (var lineStyle : lineStyles) {
            if (lineStyle.getLine() == line) {
                styleCountUpdate(type, -lineStyle.eraseStyle(type));
                break;
            }
        }
    }

    /**
     * Remove all line styles
     */
    public void eraseAllLineStyles() {
        if (lineStyles == null) {
            return;
        }
        lineStyles.clear();
        styleTypeCount.clear();
    }

    /**
     * @param indentCountMode true if the column in {@link #blocks} is the count of spaces.
     *                        In other words, the indentation level. false if the column in
     *                        {@link #blocks} are based on actual characters.
     * @see #isIndentCountMode()
     */
    public void setIndentCountMode(boolean indentCountMode) {
        this.indentCountMode = indentCountMode;
    }

    /**
     * @see #setIndentCountMode(boolean)
     */
    public boolean isIndentCountMode() {
        return indentCountMode;
    }

    /**
     * Do some extra work before finally sending the result to editor.
     */
    public void finishBuilding() {
        if (blocks != null) {
            int pre = -1;
            var sort = false;
            for (int i = 0; i < blocks.size() - 1; i++) {
                var cur = blocks.get(i + 1).endLine;
                if (pre > cur) {
                    sort = true;
                    break;
                }
                pre = cur;
            }
            if (sort) {
                Collections.sort(blocks, CodeBlock.COMPARATOR_END);
            }
            blocksByStart = new ArrayList<>(blocks);
            Collections.sort(blocksByStart, CodeBlock.COMPARATOR_START);
        } else {
            blocksByStart = null;
        }
        if (lineStyles != null) {
            Collections.sort(lineStyles);
        }
    }

}
