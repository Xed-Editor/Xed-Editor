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

package io.github.rosemoe.sora.lang.completion

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable


object SimpleCompletionIconDrawer {
    @JvmStatic
    @JvmOverloads
    fun draw(kind: CompletionItemKind, circle: Boolean = true): Drawable {
        return CircleDrawable(kind, circle)
    }
}


internal class CircleDrawable(kind: CompletionItemKind, circle: Boolean) :
    Drawable() {
    private val mPaint: Paint
    private val mTextPaint: Paint
    private val mKind: CompletionItemKind
    private val mCircle: Boolean

    init {
        mKind = kind
        mCircle = circle
        mPaint = Paint().apply {
            isAntiAlias = true
            color = kind.defaultDisplayBackgroundColor.toInt()
        }
        mTextPaint = Paint().apply {
            color = -0x1
            isAntiAlias = true
            textSize = Resources.getSystem()
                .displayMetrics.density * 14
            textAlign = Paint.Align.CENTER
        }

    }

    override fun draw(canvas: Canvas) {
        val width = bounds.right.toFloat()
        val height = bounds.bottom.toFloat()
        if (mCircle) {
            canvas.drawCircle(width / 2, height / 2, width / 2, mPaint)
        } else {
            canvas.drawRect(0f, 0f, width, height, mPaint)
        }
        canvas.save()
        canvas.translate(width / 2f, height / 2f)
        val textCenter = -(mTextPaint.descent() + mTextPaint.ascent()) / 2f
        canvas.drawText(mKind.getDisplayChar(), 0f, textCenter, mTextPaint)
        canvas.restore()
    }

    override fun setAlpha(p1: Int) {
        mPaint.alpha = p1
        mTextPaint.alpha = p1
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mTextPaint.colorFilter = colorFilter
    }

    @Deprecated(
        "Deprecated in Java",
        ReplaceWith("PixelFormat.OPAQUE", "android.graphics.PixelFormat")
    )
    override fun getOpacity(): Int {
        return PixelFormat.OPAQUE
    }


}