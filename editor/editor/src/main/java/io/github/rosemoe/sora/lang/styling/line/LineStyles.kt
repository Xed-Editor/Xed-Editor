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

package io.github.rosemoe.sora.lang.styling.line

/**
 * Store the styles on a given line
 *
 * @author Rosemoe
 */
class LineStyles(override var line: Int) : LineAnchorStyle(line) {

    private val styles = mutableListOf<LineAnchorStyle>()

    /**
     * Add a new style object. Note that style object of a given class is allowed to add once.
     * eg. You can not add two [LineBackground] objects even when they are exactly the same
     */
    fun addStyle(style: LineAnchorStyle): Int {
        if (style is LineStyles) {
            throw IllegalArgumentException("Can not add LineStyles object")
        }
        if (style.line != line) {
            throw IllegalArgumentException("target line differs from this object")
        }
        var result = 1
        if (findOne(style.javaClass) != null) {
            eraseStyle(style.javaClass)
            result = 0
        }
        styles.add(style)
        return result
    }

    /**
     * Erase style of the given type
     */
    fun <T : LineAnchorStyle> eraseStyle(type: Class<T>): Int {
        val all = findAll(type)
        styles.removeAll(all)
        return all.size
    }

    fun updateElements() {
        styles.forEach {
            it.line = line
        }
    }

    fun getElementCount() = styles.size

    fun getElementAt(index: Int) = styles[index]

    fun <T : LineAnchorStyle> findOne(type: Class<T>): T? {
        return styles.find { type.isInstance(it) } as T?
    }

    fun <T : LineAnchorStyle> findAll(type: Class<T>) = styles.filter { type.isInstance(it) }

    fun typedElementCount(type: Class<Any>): Int {
        return styles.filter { type.isInstance(it) }.size
    }

}