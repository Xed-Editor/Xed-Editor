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
package io.github.rosemoe.sora.widget;

import android.widget.OverScroller;

import androidx.annotation.NonNull;

public class EditorScroller {

    private final CodeEditor editor;

    private final OverScroller scroller;

    public EditorScroller(@NonNull CodeEditor editor) {
        scroller = new OverScroller(editor.getContext());
        this.editor = editor;
    }

    public void setEditorOffsets() {
        editor.setScrollX(scroller.getCurrX());
        editor.setScrollY(scroller.getCurrY());
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        scroller.startScroll(startX, startY, dx, dy);
        setEditorOffsets();
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        scroller.startScroll(startX, startY, dx, dy, duration);
        setEditorOffsets();
    }

    public void forceFinished(boolean finished) {
        scroller.forceFinished(finished);
        setEditorOffsets();
    }

    public void abortAnimation() {
        scroller.abortAnimation();
        setEditorOffsets();
    }

    public boolean isFinished() {
        return scroller.isFinished();
    }

    public int getCurrX() {
        return scroller.getCurrX();
    }

    public int getCurrY() {
        return scroller.getCurrY();
    }

    public int getFinalX() {
        return scroller.getFinalX();
    }

    public int getFinalY() {
        return scroller.getFinalY();
    }

    public int getStartX() {
        return scroller.getStartX();
    }

    public int getStartY() {
        return scroller.getStartY();
    }

    public float getCurrVelocity() {
        return scroller.getCurrVelocity();
    }

    public boolean computeScrollOffset() {
        var computed = scroller.computeScrollOffset();
        if (computed) {
            setEditorOffsets();
        }
        return computed;
    }

    public void fling(int startX, int startY, int velocityX, int velocityY,
                      int minX, int maxX, int minY, int maxY, int overX, int overY) {
        scroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, overX, overY);
        setEditorOffsets();
    }

    public boolean isOverScrolled() {
        return scroller.isOverScrolled();
    }

    public OverScroller getImplScroller() {
        return scroller;
    }
}
