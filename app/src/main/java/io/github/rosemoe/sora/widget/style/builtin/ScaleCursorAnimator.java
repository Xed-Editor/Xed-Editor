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
package io.github.rosemoe.sora.widget.style.builtin;

import android.animation.ValueAnimator;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.style.CursorAnimator;

/**
 * Scale-Up/Scale-Down cursor animation
 *
 * @author Dmitry Rubtsov
 */
public class ScaleCursorAnimator implements CursorAnimator, ValueAnimator.AnimatorUpdateListener {

    private final CodeEditor editor;
    private final long duration;

    private ValueAnimator scaleAnimator;
    private long lastAnimateTime;
    private float lineHeight, lineBottom;
    private float startX, startY, endX, endY;

    public ScaleCursorAnimator(CodeEditor editor) {
        this.editor = editor;
        this.scaleAnimator = new ValueAnimator();
        this.duration = 180;
    }

    @Override
    public void markStartPos() {
        int line = editor.getCursor().getLeftLine();
        lineHeight = editor.getLayout().getRowCountForLine(line) * editor.getRowHeight();
        lineBottom = editor.getLayout().getCharLayoutOffset(line, editor.getText().getColumnCount(line))[0];

        float[] pos = editor.getLayout().getCharLayoutOffset(
                editor.getCursor().getLeftLine(),
                editor.getCursor().getLeftColumn()
        );
        startX = pos[1] + editor.measureTextRegionOffset();
        startY = pos[0];
    }

    @Override
    public boolean isRunning() {
        return scaleAnimator.isRunning();
    }

    @Override
    public void cancel() {
        scaleAnimator.cancel();
    }

    @Override
    public void markEndPos() {
        if (!editor.isCursorAnimationEnabled()) {
            return;
        }
        if (isRunning()) {
            cancel();
        }
        if (System.currentTimeMillis() - lastAnimateTime < 100) {
            return;
        }
        scaleAnimator.removeAllUpdateListeners();

        int line = editor.getCursor().getLeftLine();
        lineHeight = editor.getLayout().getRowCountForLine(line) * editor.getRowHeight();
        lineBottom = editor.getLayout().getCharLayoutOffset(line, editor.getText().getColumnCount(line))[0];

        float[] pos = editor.getLayout().getCharLayoutOffset(
                editor.getCursor().getLeftLine(),
                editor.getCursor().getLeftColumn()
        );
        endX = pos[1] + editor.measureTextRegionOffset();
        endY = pos[0];

        if (editor.getInsertHandleDescriptor().position.isEmpty()) {
            scaleAnimator = ValueAnimator.ofFloat(0f, 1.0f);
            scaleAnimator.setDuration(duration);
        } else {
            scaleAnimator = ValueAnimator.ofFloat(1.0f, 0f, 1.0f);
            scaleAnimator.setDuration(duration * 2);
        }
        scaleAnimator.addUpdateListener(this);
    }

    @Override
    public void start() {
        if (!editor.isCursorAnimationEnabled() || System.currentTimeMillis() - lastAnimateTime < 100 || editor.getInsertHandleDescriptor().position.isEmpty()) {
            lastAnimateTime = System.currentTimeMillis();
            return;
        }
        if (startX == endX && startY == endY && !editor.getInsertHandleDescriptor().position.isEmpty()) {
            return;
        }
        scaleAnimator.start();
        lastAnimateTime = System.currentTimeMillis();
    }

    private boolean shouldReturnEndValue() {
        if (!scaleAnimator.isRunning() || editor.getInsertHandleDescriptor().position.isEmpty()) {
            return true;
        }
        if (scaleAnimator.getDuration() == duration) {
            return true;
        } else {
            return scaleAnimator.getCurrentPlayTime() > duration;
        }
    }

    @Override
    public float animatedX() {
        if (shouldReturnEndValue()) {
            return endX;
        }
        return startX;
    }

    @Override
    public float animatedY() {
        if (shouldReturnEndValue()) {
            return endY;
        }
        return startY;
    }

    @Override
    public float animatedLineHeight() {
        return lineHeight;
    }

    @Override
    public float animatedLineBottom() {
        return lineBottom;
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        editor.getHandleStyle().setScale((float) animation.getAnimatedValue());
        editor.postInvalidateOnAnimation();
    }
}
