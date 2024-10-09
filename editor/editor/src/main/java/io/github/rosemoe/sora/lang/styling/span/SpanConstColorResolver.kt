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

import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.color.ConstColor

/**
 * Override span's foreground and background with constant color.
 * @param foreground Override foreground color, `0` for no override
 * @param background Override background color, `0` for no override
 *
 * @author Rosemoe
 */
class SpanConstColorResolver(foreground: Int = 0, background: Int = 0) : SpanColorResolver {

    private val foregroundColor = if (foreground == 0) null else ConstColor(foreground)

    private val backgroundColor = if (background == 0) null else ConstColor(background)

    override fun getForegroundColor(span: Span) = foregroundColor

    override fun getBackgroundColor(span: Span) = backgroundColor


}