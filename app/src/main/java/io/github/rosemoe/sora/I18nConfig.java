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
package io.github.rosemoe.sora;

import android.content.Context;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;

/**
 * Map editor built-in string resources to your given string resource. Editor string resource has
 * limited i18n function, as it only contains English and Chinese.
 * <p>
 * Note that you should configure this before creating editor instances
 *
 * @author Rosemoe
 */
public class I18nConfig {

    private static final SparseIntArray mapping = new SparseIntArray();

    /**
     * Map the given editor resId to new one
     */
    public static void mapTo(int originalResId, int newResId) {
        mapping.put(originalResId, newResId);
    }

    /**
     * Get mapped resource id or itself
     */
    public static int getResourceId(int resId) {
        int newResource = mapping.get(resId);
        if (newResource == 0) {
            return resId;
        }
        return newResource;
    }

    /**
     * Get mapped resource string
     */
    public static String getString(@NonNull Context context, int resId) {
        return context.getString(getResourceId(resId));
    }

}
