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
package io.github.rosemoe.sora.util;

import android.content.Context;
import android.content.res.TypedArray;
import android.os.Build;
import android.util.Log;
import android.view.ViewConfiguration;

import androidx.annotation.NonNull;

public class ViewUtils {

    private final static String LOG_TAG = "ViewUtils";

    public final static float DEFAULT_SCROLL_FACTOR = 32f;

    public final static long HOVER_TOOLTIP_SHOW_TIMEOUT = 1000;

    public static final int HOVER_TAP_SLOP = 20;

    public static float getVerticalScrollFactor(@NonNull Context context) {
        float verticalScrollFactor;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            var configuration = ViewConfiguration.get(context);
            verticalScrollFactor = configuration.getScaledVerticalScrollFactor();
        } else {
            TypedArray a = null;
            try {
                a = context.obtainStyledAttributes(new int[]{android.R.attr.listPreferredItemHeight});
                verticalScrollFactor = a.getDimension(0, DEFAULT_SCROLL_FACTOR);
            } catch (Exception e) {
                Log.e(LOG_TAG, "Failed to get vertical scroll factor, using default.", e);
                verticalScrollFactor = DEFAULT_SCROLL_FACTOR;
            } finally {
                if (a != null) {
                    a.recycle();
                }
            }
        }
        return verticalScrollFactor;
    }

}
