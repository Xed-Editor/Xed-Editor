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
package io.github.rosemoe.sora.widget.style.builtin;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle;

public class HandleStyleDrop implements SelectionHandleStyle {

    private final Drawable drawable;
    private final int width;
    private final int height;
    private int lastColor = 0;

    private int alpha = 255;
    private float scaleFactor = 1.0f;

    public HandleStyleDrop(Context context) {
        drawable = context.getDrawable(R.drawable.ic_sora_handle_drop).mutate();
        width = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20f, context.getResources().getDisplayMetrics());
        height = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30f, context.getResources().getDisplayMetrics());
    }

    @Override
    public void draw(@NonNull Canvas canvas, int handleType, float x, float y, int rowHeight, int color, @NonNull HandleDescriptor descriptor) {
        if (lastColor != color) {
            lastColor = color;
            drawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP));
        }
        var left = (int) (x - (width * scaleFactor) / 2);
        var top = (int) y;
        var right = (int) (x + (width * scaleFactor) / 2);
        var bottom = (int) (y + height * scaleFactor);
        drawable.setBounds(left, top, right, bottom);
        drawable.setAlpha(alpha);
        drawable.draw(canvas);
        descriptor.set(left, top, right, bottom, ALIGN_CENTER);
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setScale(float factor) {
        this.scaleFactor = factor;
    }
}
