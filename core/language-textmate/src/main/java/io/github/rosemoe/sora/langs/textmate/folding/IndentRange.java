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
package io.github.rosemoe.sora.langs.textmate.folding;

import java.util.ArrayList;
import java.util.List;

import io.github.rosemoe.sora.lang.analysis.AsyncIncrementalAnalyzeManager;
import io.github.rosemoe.sora.text.Content;

import org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp;
import org.eclipse.tm4e.core.internal.oniguruma.OnigResult;


public class IndentRange {
    public static final int MAX_LINE_NUMBER = 0xFFFFFF;
    public static final int MAX_FOLDING_REGIONS = 0xFFFF;
    public static final int MASK_INDENT = 0xFF000000;

    // START sora-editor note
    // Change String to char[] and int
    // END sora-editor note

    public static int computeStartColumn(char[] line, int len, int tabSize) {
        int column = 0;
        int i = 0;

        while (i < len) {
            char chCode = line[i];
            if (chCode == ' ') {
                column++;
            } else if (chCode == '\t') {
                column += tabSize;
            } else {
                break;
            }
            i++;
        }

        if (i == len) {
            // line only consists of whitespace
            return -1;
        }

        return column;
    }

    /**
     * @return :
     * - -1 => the line consists of whitespace
     * - otherwise => the indent level is returned value
     */
    public static int computeIndentLevel(char[] line, int len, int tabSize) {
        int indent = 0;
        int i = 0;

        while (i < len) {
            char chCode = line[i];
            if (chCode == ' ') {
                indent++;
            } else if (chCode == '\t') {
                indent = indent - indent % tabSize + tabSize;
            } else {
                break;
            }
            i++;
        }

        if (i == len) {
            // line only consists of whitespace
            return -1;
        }

        return indent;
    }

    public static FoldingRegions computeRanges(Content model, int tabSize, boolean offSide, FoldingHelper helper, OnigRegExp pattern, AsyncIncrementalAnalyzeManager<?, ?>.CodeBlockAnalyzeDelegate delegate) throws Exception {

        RangesCollector result = new RangesCollector(/*tabSize*/);

        List<PreviousRegion> previousRegions = new ArrayList<>();
        int line = model.getLineCount() + 1;
        // sentinel, to make sure there's at least one entry
        previousRegions.add(new PreviousRegion(-1, line, line));

        for (line = model.getLineCount() - 1; line >= 0 && delegate.isNotCancelled(); line--) {
            int indent = helper.getIndentFor(line);//computeIndentLevel(model.getLine(line).getBackingCharArray(), model.getColumnCount(line), tabSize);
            PreviousRegion previous = previousRegions.get(previousRegions.size() - 1);
            if (indent == -1) {
                if (offSide) {
                    // for offSide languages, empty lines are associated to the previous block
                    // note: the next block is already written to the results, so this only
                    // impacts the end position of the block before
                    previous.endAbove = line;
                }
                continue; // only whitespace
            }
            OnigResult m;
            if (pattern != null && (m = helper.getResultFor(line)) != null) {
                // folding pattern match
                if (m.count() >= 2) { // start pattern match
                    // discard all regions until the folding pattern
                    int i = previousRegions.size() - 1;
                    while (i > 0 && previousRegions.get(i).indent != -2) {
                        i--;
                    }
                    if (i > 0) {
                        //??? previousRegions.length = i + 1;
                        previous = previousRegions.get(i);

                        // new folding range from pattern, includes the end line
                        result.insertFirst(line, previous.line, indent);
                        previous.line = line;
                        previous.indent = indent;
                        previous.endAbove = line;
                        continue;
                    } else {
                        // no end marker found, treat line as a regular line
                    }
                } else { // end pattern match
                    previousRegions.add(new PreviousRegion(-2, line, line));
                    continue;
                }
            }
            if (previous.indent > indent) {
                // discard all regions with larger indent
                do {
                    previousRegions.remove(previousRegions.size() - 1);
                    previous = previousRegions.get(previousRegions.size() - 1);
                } while (previous.indent > indent);

                // new folding range
                int endLineNumber = previous.endAbove - 1;
                if (endLineNumber - line >= 1) { // needs at east size 1
                    result.insertFirst(line, endLineNumber, indent);
                }
            }
            if (previous.indent == indent) {
                previous.endAbove = line;
            } else { // previous.indent < indent
                // new region with a bigger indent
                previousRegions.add(new PreviousRegion(indent, line, line));
            }
        }
        return result.toIndentRanges(model);
    }
}
