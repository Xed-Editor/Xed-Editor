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

package io.github.rosemoe.sora.widget.rendering

import androidx.collection.MutableIntList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Cache for editor rendering, including line-based data and measure
 * cache for recently accessed lines.
 *
 * @author Rosemoe
 */
class RenderCache {

    private val lock = ReentrantLock()
    private val lines = MutableIntList()
    private val cache = mutableListOf<MeasureCacheItem>()
    private var maxCacheCount = 75

    fun getOrCreateMeasureCache(line: Int): MeasureCacheItem {
        return queryMeasureCache(line) ?: run {
            lock.withLock {
                MeasureCacheItem(line, null, 0L).also {
                    cache.add(it)
                    while (cache.size > maxCacheCount && cache.isNotEmpty()) {
                        cache.removeAt(0)
                    }
                }
            }
        }
    }

    fun queryMeasureCache(line: Int) =
        lock.withLock {
            cache.firstOrNull { it.line == line }.also {
                if (it != null) {
                    cache.remove(it)
                    cache.add(it)
                }
            }
        }

    fun getStyleHash(line: Int) = lines[line]

    fun setStyleHash(line: Int, hash: Int) {
        lines[line] = hash
    }

    fun updateForInsertion(startLine: Int, endLine: Int) {
        if (startLine != endLine) {
            if (endLine - startLine == 1) {
                lines.add(startLine, 0)
            } else {
                lines.addAll(startLine, IntArray(endLine - startLine) { 0 })
            }
            lock.withLock {
                cache.forEach {
                    if (it.line > startLine) {
                        it.line += endLine - startLine
                    }
                }
            }
        }
    }

    fun updateForDeletion(startLine: Int, endLine: Int) {
        if (startLine != endLine) {
            lines.removeRange(startLine, endLine)
            lock.withLock {
                cache.removeAll { it.line in startLine..endLine }
                cache.forEach {
                    if (it.line > endLine) {
                        it.line -= endLine - startLine
                    }
                }
            }
        }
    }

    fun reset(lineCount: Int) {
        if (lines.size > lineCount) {
            lines.removeRange(lineCount, lines.size)
        } else if (lines.size < lineCount) {
            repeat(lineCount - lines.size) {
                lines.add(0)
            }
        }
        lines.indices.forEach { lines[it] = 0 }
        lock.withLock {
            cache.clear()
        }
    }

}