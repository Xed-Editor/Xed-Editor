/*******************************************************************************
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
 ******************************************************************************/

package io.github.rosemoe.sora.lang.styling.span

import android.graphics.Canvas
import android.graphics.Paint
import io.github.rosemoe.sora.annotations.Experimental
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * External renderer for spans.
 *
 *
 * Users can attach this renderer to any span so that you can draw
 * extra content inside the rectangle on canvas of the span.
 *
 *
 * However, any implementation of this class should not create many new objects
 * in its methods but initialize your required resources when the renderer is
 * created.
 *
 *
 * Meanwhile, the renderer should be consistent with editor's current
 * color scheme. Use colors defined in your color schemes if possible.
 *
 *
 * Also, try to create universal renderers for a certain effect.
 *
 *
 * Note: the [Paint] object should not be modified or used to draw objects on the canvas
 * It is provided only for measuring. Use another [Paint] created by yourself instead.
 */
@Experimental
interface SpanExternalRenderer : SpanExt {
    fun requirePreDraw(): Boolean

    fun requirePostDraw(): Boolean

    /**
     * Called when the editor draws the given region.
     *
     * @param canvas      The canvas to draw
     * @param paint       Paint for measuring
     * @param colorScheme Current color scheme
     * @param preOrPost   True for preDraw, False for postDraw
     */
    fun draw(canvas: Canvas?, paint: Paint?, colorScheme: EditorColorScheme?, preOrPost: Boolean)
}