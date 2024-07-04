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

import android.animation.Animator;
import android.animation.ValueAnimator;

import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.style.CursorAnimator;

/**
 * Fade-in/Fade-out cursor animation
 *
 * @author Dmitry Rubtsov
 */
public class FadeCursorAnimator implements CursorAnimator, ValueAnimator.AnimatorUpdateListener {

    private final CodeEditor editor;
    private final long duration;

    private ValueAnimator fadeInAnimator;
    private ValueAnimator fadeOutAnimator;

    private boolean phaseEnded;
    private long lastAnimateTime;
    private float lineHeight, lineBottom;
    private float startX, startY, endX, endY;

    public FadeCursorAnimator(CodeEditor editor) {
        this.editor = editor;
        this.fadeInAnimator = new ValueAnimator();
        this.fadeOutAnimator = new ValueAnimator();
        this.duration = 200;
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
        return fadeInAnimator.isRunning() || fadeOutAnimator.isRunning();
    }

    @Override
    public void cancel() {
        fadeOutAnimator.cancel();
        fadeInAnimator.cancel();
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
        fadeOutAnimator.removeAllUpdateListeners();
        fadeInAnimator.removeAllUpdateListeners();

        int line = editor.getCursor().getLeftLine();
        lineHeight = editor.getLayout().getRowCountForLine(line) * editor.getRowHeight();
        lineBottom = editor.getLayout().getCharLayoutOffset(line, editor.getText().getColumnCount(line))[0];

        float[] pos = editor.getLayout().getCharLayoutOffset(
                editor.getCursor().getLeftLine(),
                editor.getCursor().getLeftColumn()
        );
        endX = pos[1] + editor.measureTextRegionOffset();
        endY = pos[0];

        fadeOutAnimator = ValueAnimator.ofInt(255, 0);
        fadeOutAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
            }

            @Override
            public void onAnimationStart(Animator animator) {
                phaseEnded = false;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                phaseEnded = true;
            }
        });
        fadeOutAnimator.addUpdateListener(this);
        fadeOutAnimator.setDuration(duration);

        fadeInAnimator = ValueAnimator.ofInt(0, 255);
        fadeInAnimator.addUpdateListener(this);
        fadeInAnimator.setStartDelay(duration);
        fadeInAnimator.setDuration(duration);
    }

    @Override
    public void start() {
        if (!editor.isCursorAnimationEnabled() || System.currentTimeMillis() - lastAnimateTime < 100) {
            lastAnimateTime = System.currentTimeMillis();
            return;
        }
        fadeOutAnimator.start();
        fadeInAnimator.start();
        lastAnimateTime = System.currentTimeMillis();
    }

    @Override
    public float animatedX() {
        if (phaseEnded || editor.getInsertHandleDescriptor().position.isEmpty()) {
            return endX;
        }
        return startX;
    }

    @Override
    public float animatedY() {
        if (phaseEnded || editor.getInsertHandleDescriptor().position.isEmpty()) {
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
        editor.getHandleStyle().setAlpha((int) animation.getAnimatedValue());
        editor.postInvalidateOnAnimation();
    }
}
