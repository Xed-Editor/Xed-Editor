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

/*******************************************************************************
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2022  Rosemoe
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

package io.github.rosemoe.sora.lang.styling.inlayHint

import android.graphics.Canvas
import io.github.rosemoe.sora.graphics.Paint
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * A general inlay hint of text. The hint text will be displayed in a round colored rect.
 *
 * @author Rosemoe
 */
class TextInlayHint(val text: String) : InlayHint(InlayHintLayoutType.IN_LINE) {

    override fun onMeasure(
        paint: Paint,
        textMetrics: android.graphics.Paint.FontMetricsInt,
        lineHeight: Int,
        baseline: Float
    ): Float {
        val margin = paint.spaceWidth * 0.8f
        val textSize = paint.textSize
        paint.setTextSizeWrapped(textSize * 0.75f)
        val width = paint.measureText(text) + margin * 3
        paint.setTextSizeWrapped(textSize)
        return width
    }

    override fun onRender(
        canvas: Canvas,
        paint: Paint,
        textMetrics: android.graphics.Paint.FontMetricsInt,
        colorScheme: EditorColorScheme,
        lineHeight: Int,
        baseline: Float,
        measuredWidth: Float
    ) {
        val margin = paint.spaceWidth * 0.8f
        val textSize = paint.textSize
        paint.setTextSizeWrapped(textSize * 0.75f)

        val myLineHeight = paint.descent() - paint.ascent()
        val myBaseline = lineHeight / 2f - myLineHeight / 2f + paint.descent()
        paint.color = colorScheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_BACKGROUND)
        canvas.drawRoundRect(
            margin,
            lineHeight / 2f - myLineHeight / 2f,
            measuredWidth - margin,
            lineHeight / 2f + myLineHeight / 2f,
            0.15f,
            0.15f,
            paint
        )
        paint.color = colorScheme.getColor(EditorColorScheme.TEXT_INLAY_HINT_FOREGROUND)
        canvas.drawText(text, margin * 1.5f, myBaseline, paint)

        paint.setTextSizeWrapped(textSize)
    }

}