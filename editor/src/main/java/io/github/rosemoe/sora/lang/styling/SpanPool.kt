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

package io.github.rosemoe.sora.lang.styling

import java.util.concurrent.ArrayBlockingQueue

/**
 * A pool of [Span] objects. Subclasses of [Span]s can use this pool to recycle
 * and reuse [Span] objects of a specific type.
 *
 * **Note**: Instances of this class should be stored in static variables.
 *
 * @param capacity The capacity of the pool.
 * @property factory A factory method to create new instances of [SpanT].
 * @author Akash Yadav
 */
open class SpanPool<SpanT : Span> @JvmOverloads constructor(
  capacity: Int = DEFAULT_CAPACITY,
  private val factory: (column: Int, style: Long) -> SpanT
) {

  private val cacheQueue = ArrayBlockingQueue<SpanT>(capacity)

  companion object {

    /**
     * Small capacity (8192 objects). This should be used for spans that will not
     * be used too frequently (for example, [StaticColorSpan]).
     */
    const val CAPACITY_SMALL = 8192

    /**
     * Small capacity ([CAPACITY_SMALL] * 2 objects). This should be used for spans
     * that will be used frequently (for example, [Span]).
     */
    const val CAPACITY_LARGE = CAPACITY_SMALL * 2

    /**
     * The default pool capacity. Same as [CAPACITY_LARGE].
     */
    const val DEFAULT_CAPACITY = CAPACITY_LARGE
  }

  /**
   * Return the given span to the pool. This method should not be called directly.
   * Instead, call [Span.recycle] and it should automatically return itself to the
   * pool.
   *
   * @param span The [SpanT] to recycle.
   * @return Wheter the span was recycled successfully.
   */
  open fun offer(span: SpanT): Boolean {
    return cacheQueue.offer(span)
  }

  /**
   * Returns a recycled span or creates a new one if the pool is empty.
   *
   * @param column The new column index for the span.
   * @param style The new style for the span.
   * @return The recycled [SpanT], or a new instance of [SpanT] if the pool is empty.
   */
  open fun obtain(column: Int, style: Long): SpanT {
    return cacheQueue.poll()?.also {
      it.column = column
      it.style = style
    } ?: factory(column, style)
  }
}