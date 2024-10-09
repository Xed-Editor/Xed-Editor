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
 * Default cursor animation implementation
 *
 * @author Rosemoe
 */
public class MoveCursorAnimator implements CursorAnimator, ValueAnimator.AnimatorUpdateListener {

    private final CodeEditor editor;
    private final long duration;
    private ValueAnimator animatorX;
    private ValueAnimator animatorY;
    private ValueAnimator animatorBgBottom;
    private ValueAnimator animatorBackground;
    private float startX, startY, startSize, startBottom;
    private long lastAnimateTime;

    public MoveCursorAnimator(CodeEditor editor) {
        this.editor = editor;
        animatorX = new ValueAnimator();
        animatorY = new ValueAnimator();
        animatorBackground = new ValueAnimator();
        animatorBgBottom = new ValueAnimator();
        duration = 120;
    }

    private int getHeightOfRows(int rowCount) {
        return editor.getRowHeight() * rowCount;
    }

    @Override
    public void markStartPos() {
        var line = editor.getCursor().getLeftLine();
        float[] pos = editor.getLayout().getCharLayoutOffset(line, editor.getCursor().getLeftColumn());
        startX = editor.measureTextRegionOffset() + pos[1];
        startY = pos[0] - minusHeight();
        startSize = getHeightOfRows(editor.getLayout().getRowCountForLine(line));
        startBottom = editor.getLayout().getCharLayoutOffset(line, editor.getText().getColumnCount(line))[0];
    }

    @Override
    public boolean isRunning() {
        return animatorX.isRunning() || animatorY.isRunning() || animatorBackground.isRunning() || animatorBgBottom.isRunning();
    }

    @Override
    public void cancel() {
        animatorX.cancel();
        animatorY.cancel();
        animatorBackground.cancel();
        animatorBgBottom.cancel();
    }

    private float minusHeight() {
        return editor.getProps().textBackgroundWrapTextOnly ? editor.getLineSpacingPixels() / 2f : 0f;
    }

    @Override
    public void markEndPos() {
        if (!editor.isCursorAnimationEnabled()) {
            return;
        }
        if (isRunning()) {
            startX = animatedX();
            startY = animatedY();
            startSize = (float) animatorBackground.getAnimatedValue();
            startBottom = (float) animatorBgBottom.getAnimatedValue();
            cancel();
        }
        if (System.currentTimeMillis() - lastAnimateTime < 100) {
            return;
        }
        var line = editor.getCursor().getLeftLine();
        animatorX.removeAllUpdateListeners();
        float[] pos = editor.getLayout().getCharLayoutOffset(editor.getCursor().getLeftLine(), editor.getCursor().getLeftColumn());

        animatorX = ValueAnimator.ofFloat(startX, (pos[1] + editor.measureTextRegionOffset()));
        animatorY = ValueAnimator.ofFloat(startY, pos[0] - minusHeight());

        animatorBackground = ValueAnimator.ofFloat(startSize, getHeightOfRows(editor.getLayout().getRowCountForLine(editor.getCursor().getLeftLine())));
        animatorBgBottom = ValueAnimator.ofFloat(startBottom, editor.getLayout().getCharLayoutOffset(line, editor.getText().getColumnCount(line))[0]);

        animatorX.addUpdateListener(this);

        animatorX.setDuration(duration);
        animatorY.setDuration(duration);
        animatorBackground.setDuration(duration);
        animatorBgBottom.setDuration(duration);
    }

    @Override
    public void start() {
        if (!editor.isCursorAnimationEnabled() || System.currentTimeMillis() - lastAnimateTime < 100) {
            lastAnimateTime = System.currentTimeMillis();
            return;
        }
        animatorX.start();
        animatorY.start();
        animatorBackground.start();
        animatorBgBottom.start();

        lastAnimateTime = System.currentTimeMillis();
    }

    @Override
    public float animatedX() {
        return (float) animatorX.getAnimatedValue();
    }

    @Override
    public float animatedY() {
        return (float) animatorY.getAnimatedValue();
    }

    @Override
    public float animatedLineHeight() {
        return (float) animatorBackground.getAnimatedValue();
    }

    @Override
    public float animatedLineBottom() {
        return (float) animatorBgBottom.getAnimatedValue();
    }

    @Override
    public void onAnimationUpdate(ValueAnimator animation) {
        editor.postInvalidateOnAnimation();
    }
}
