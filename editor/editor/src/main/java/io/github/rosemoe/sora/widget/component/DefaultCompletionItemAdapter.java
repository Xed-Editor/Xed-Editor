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
package io.github.rosemoe.sora.widget.component;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import io.github.rosemoe.sora.R;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Default adapter to display results
 *
 * @author Rose
 */
public final class DefaultCompletionItemAdapter extends EditorCompletionAdapter {

    @Override
    public int getItemHeight() {
        // 45 dp
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 45, getContext().getResources().getDisplayMetrics());
    }

    @Override
    public View getView(int pos, View view, ViewGroup parent, boolean isCurrentCursorPosition) {
        if (view == null) {
            view = LayoutInflater.from(getContext()).inflate(R.layout.default_completion_result_item, parent, false);
        }
        var item = getItem(pos);

        boolean isDrakMode = isDarkMode(view.getContext());

        TextView tv = view.findViewById(R.id.result_item_label);
        tv.setText(item.label);

        if (!isDrakMode) {
            tv.setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY));
        }

        tv = view.findViewById(R.id.result_item_desc);
        tv.setText(item.desc);

        if (!isDrakMode) {
            tv.setTextColor(getThemeColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY));
        }


        view.setTag(pos);
        Drawable rippleDrawable;

        if (isCurrentCursorPosition) {
            int backgroundColor = isDrakMode
                    ? Color.argb(100, 255, 255, 255)
                    : Color.argb(100, 0, 0, 0);
            ColorDrawable background = new ColorDrawable(backgroundColor);

            rippleDrawable = new RippleDrawable(
                    ColorStateList.valueOf(Color.GRAY),
                    background,
                    null
            );
        } else {
            int backgroundColor = isDrakMode
                    ? Color.parseColor("#1C1B20")
                    : Color.WHITE;
            ColorDrawable background = new ColorDrawable(backgroundColor);

            rippleDrawable = new RippleDrawable(
                    ColorStateList.valueOf(Color.GRAY), // Ripple color
                    background, // Background color
                    null // No mask
            );
        }

        view.setBackground(rippleDrawable);

        ImageView iv = view.findViewById(R.id.result_item_image);
        iv.setImageDrawable(item.icon);
        return view;
    }

    public boolean isDarkMode(Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

}
