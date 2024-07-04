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

package io.github.rosemoe.sora.widget

import android.view.MotionEvent
import io.github.rosemoe.sora.util.IntPair

const val REGION_OUTBOUND = 0
const val REGION_LINE_NUMBER = 1
const val REGION_SIDE_ICON = 2
const val REGION_DIVIDER_MARGIN = 3
const val REGION_DIVIDER = 4
const val REGION_TEXT = 5

const val IN_BOUND = 0
const val OUT_BOUND = 1

fun CodeEditor.resolveTouchRegion(event: MotionEvent) = resolveTouchRegion(event, -1)

fun CodeEditor.resolveTouchRegion(event: MotionEvent, pointerIndex: Int = -1): Long {
    val x = (if (pointerIndex == -1) event.x else event.getX(pointerIndex)) + offsetX
    val y = (if (pointerIndex == -1) event.y else event.getY(pointerIndex)) + offsetY
    val lineNumberWidth = measureLineNumber()
    val iconWidth = if (renderer.hasSideHintIcons()) rowHeight else 0
    val textOffset = measureTextRegionOffset()
    val region = when {
        x < 0f -> REGION_OUTBOUND
        x in 0f..lineNumberWidth -> REGION_LINE_NUMBER
        x in lineNumberWidth..lineNumberWidth + iconWidth -> REGION_SIDE_ICON
        x in lineNumberWidth + iconWidth..lineNumberWidth + iconWidth + dividerMarginLeft
                || x in (lineNumberWidth + iconWidth + dividerMarginLeft + dividerWidth)..(lineNumberWidth + iconWidth + dividerMarginLeft + dividerMarginRight + dividerWidth)
        -> REGION_DIVIDER_MARGIN

        x in lineNumberWidth + iconWidth + dividerMarginLeft..lineNumberWidth + iconWidth + dividerMarginLeft + dividerWidth -> REGION_DIVIDER
        x in textOffset..(scrollMaxX + width).toFloat() -> REGION_TEXT
        else -> if (isWordwrap && x in 0f..width.toFloat()) REGION_TEXT else REGION_OUTBOUND
    }
    val bound = if (y >= 0 && y <= scrollMaxY + height / 2) {
        IN_BOUND
    } else {
        OUT_BOUND
    }
    return IntPair.pack(region, bound)
}