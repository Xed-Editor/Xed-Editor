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

package io.github.rosemoe.sora.util

import io.github.rosemoe.sora.lang.styling.Span
import io.github.rosemoe.sora.lang.styling.span.SpanColorResolver
import io.github.rosemoe.sora.lang.styling.span.SpanExtAttrs
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Utility methods for use in editor renderer.
 *
 * @author Akash Yadav
 */
object RendererUtils {

  /**
   * Get the background color for the given span.
   */
  @JvmStatic
  fun getBackgroundColor(span: Span, colorScheme: EditorColorScheme): Int {
    val resolver = span.getSpanExt<SpanColorResolver>(SpanExtAttrs.EXT_COLOR_RESOLVER)
      ?: return colorScheme.getColor(span.backgroundColorId)

    val color = resolver.getBackgroundColor(span)
      ?: return colorScheme.getColor(span.backgroundColorId)

    return color.resolve(colorScheme)
  }

  /**
   * Get the foreground color for the given span.
   */
  @JvmStatic
  fun getForegroundColor(span: Span, colorScheme: EditorColorScheme): Int {
    val resolver = span.getSpanExt<SpanColorResolver>(SpanExtAttrs.EXT_COLOR_RESOLVER)
      ?: return colorScheme.getColor(span.foregroundColorId)

    val color = resolver.getForegroundColor(span)
      ?: return colorScheme.getColor(span.foregroundColorId)

    return color.resolve(colorScheme)
  }
}