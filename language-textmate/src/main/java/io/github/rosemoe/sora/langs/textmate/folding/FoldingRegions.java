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

import android.util.SparseIntArray;

import java.util.Stack;

public class FoldingRegions {
    private final SparseIntArray _startIndexes;
    private final SparseIntArray _endIndexes;
    private boolean _parentsComputed;
    public FoldingRegions(SparseIntArray startIndexes, SparseIntArray endIndexes) throws Exception {
        if (startIndexes.size() != endIndexes.size() || startIndexes.size() > IndentRange.MAX_FOLDING_REGIONS) {
            throw new Exception("invalid startIndexes or endIndexes size");
        }
        this._startIndexes = startIndexes;
        this._endIndexes = endIndexes;
        this._parentsComputed=false;
    }

    public int length() {
        return this._startIndexes.size();
    }

    public int getStartLineNumber(int index) {
        return this._startIndexes.get(index) & IndentRange.MAX_LINE_NUMBER;
    }

    public int getEndLineNumber(int index) {
        return this._endIndexes.get(index) & IndentRange.MAX_LINE_NUMBER;
    }


    public FoldingRegion toRegion(int index) {
        return new FoldingRegion(this, index);
    }

    private boolean isInsideLast(Stack<Integer> parentIndexes,int startLineNumber,int endLineNumber){
        int index = parentIndexes.get(parentIndexes.size() - 1);
        return this.getStartLineNumber(index) <= startLineNumber && this.getEndLineNumber(index) >= endLineNumber;

    }

    private void ensureParentIndices() throws Exception {
        if (!this._parentsComputed) {
            this._parentsComputed = true;
            Stack<Integer> parentIndexes=new Stack<>();
            for (int i = 0, len = this._startIndexes.size(); i < len; i++) {
                int startLineNumber = this._startIndexes.get(i);
                int endLineNumber = this._endIndexes.get(i);
                if (startLineNumber > IndentRange.MAX_LINE_NUMBER || endLineNumber > IndentRange.MAX_LINE_NUMBER) {
                    throw new Exception("startLineNumber or endLineNumber must not exceed " + IndentRange.MAX_LINE_NUMBER);
                }
                while (parentIndexes.size() > 0 && !isInsideLast(parentIndexes,startLineNumber, endLineNumber)) {
                    parentIndexes.pop();
                }
                int parentIndex = parentIndexes.size() > 0 ? parentIndexes.get(parentIndexes.size() - 1) : -1;
                parentIndexes.push(i);
                this._startIndexes.put(i,startLineNumber + ((parentIndex & 0xFF) << 24));
                this._endIndexes.put(i,endLineNumber + ((parentIndex & 0xFF00) << 16));
            }
        }
    }

    public int getParentIndex(int index) throws Exception {
        this.ensureParentIndices();
        int parent = ((this._startIndexes.get(index) & IndentRange.MASK_INDENT) >>> 24) + ((this._endIndexes.get(index) & IndentRange.MASK_INDENT) >>> 16);
        if (parent == IndentRange.MAX_FOLDING_REGIONS) {
            return -1;
        }
        return parent;
    }
}
