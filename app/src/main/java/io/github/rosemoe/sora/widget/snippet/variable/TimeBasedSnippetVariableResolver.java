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
package io.github.rosemoe.sora.widget.snippet.variable;

import static io.github.rosemoe.sora.text.TextUtils.padStart;

import androidx.annotation.NonNull;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Resolver for time-related variables
 *
 * @author Rosemoe
 */
public class TimeBasedSnippetVariableResolver implements ISnippetVariableResolver {

    private static String getDisplayName(int field, boolean shortType) {
        var c = Calendar.getInstance();
        var result = c.getDisplayName(field, shortType ? Calendar.SHORT : Calendar.LONG, Locale.getDefault());
        if (result == null && shortType) {
            result = c.getDisplayName(field, Calendar.LONG, Locale.getDefault());
        }
        if (result == null) {
            result = c.getDisplayName(field, shortType ? Calendar.SHORT : Calendar.LONG, Locale.US);
        }
        if (result == null) {
            // The very fallback
            result = Integer.toString(c.get(field));
        }
        return result;
    }

    @NonNull
    @Override
    public String[] getResolvableNames() {
        return new String[]{
                "CURRENT_YEAR", "CURRENT_YEAR_SHORT", "CURRENT_MONTH", "CURRENT_DATE",
                "CURRENT_HOUR", "CURRENT_MINUTE", "CURRENT_SECOND", "CURRENT_DAY_NAME",
                "CURRENT_DAY_NAME_SHORT", "CURRENT_MONTH_NAME", "CURRENT_MONTH_NAME_SHORT",
                "CURRENT_SECONDS_UNIX"
        };
    }

    @NonNull
    @Override
    public String resolve(@NonNull String name) {
        switch (name) {
            case "CURRENT_YEAR":
                return Integer.toString(Calendar.getInstance().get(Calendar.YEAR));
            case "CURRENT_YEAR_SHORT":
                return padStart(Integer.toString(Calendar.getInstance().get(Calendar.YEAR) % 100), '0', 2);
            case "CURRENT_MONTH":
                return padStart(Integer.toString(Calendar.getInstance().get(Calendar.MONTH)), '0', 2);
            case "CURRENT_DATE":
                return SimpleDateFormat.getDateInstance().format(new Date());
            case "CURRENT_HOUR":
                return padStart(Integer.toString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)), '0', 2);
            case "CURRENT_MINUTE":
                return padStart(Integer.toString(Calendar.getInstance().get(Calendar.MINUTE)), '0', 2);
            case "CURRENT_SECOND":
                return padStart(Integer.toString(Calendar.getInstance().get(Calendar.SECOND)), '0', 2);
            case "CURRENT_DAY_NAME":
                return getDisplayName(Calendar.DAY_OF_WEEK, false);
            case "CURRENT_DAY_NAME_SHORT":
                return getDisplayName(Calendar.DAY_OF_WEEK, true);
            case "CURRENT_MONTH_NAME":
                return getDisplayName(Calendar.MONTH, false);
            case "CURRENT_MONTH_NAME_SHORT":
                return getDisplayName(Calendar.MONTH, true);
            case "CURRENT_SECONDS_UNIX":
                return Long.toString(Math.round(System.currentTimeMillis() / 1000.0));
        }
        throw new IllegalArgumentException("Unsupported variable name:" + name);
    }
}
