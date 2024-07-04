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
package io.github.rosemoe.sora.widget.style;

/**
 * Interface for provide various cursor animations
 *
 * @author Rosemoe, Dmitry Rubtsov
 */
public interface CursorAnimator {

    /**
     * Mark the current cursor position as animation start position
     */
    void markStartPos();

    /**
     * Mark the current cursor position as animation end position
     */
    void markEndPos();

    /**
     * Start animation
     */
    void start();

    /**
     * Cancel animation
     */
    void cancel();

    /**
     * Check whether animation is in process
     */
    boolean isRunning();

    /**
     * The current x position of cursor in view offset
     */
    float animatedX();

    /**
     * The current y position of cursor in view offset
     */
    float animatedY();

    /**
     * Height of current line background
     */
    float animatedLineHeight();

    /**
     * Bottom Y position in view offset of current line background
     */
    float animatedLineBottom();
}
