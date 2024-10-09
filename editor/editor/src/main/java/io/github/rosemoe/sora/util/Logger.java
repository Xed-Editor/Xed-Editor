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

import android.util.Log;

import java.util.Map;
import java.util.WeakHashMap;

public class Logger {

    private static final Map<String, Logger> map = new WeakHashMap<>();
    private final String name;

    private Logger(String name) {
        this.name = name;
    }

    public synchronized static Logger instance(String name) {
        var logger = map.get(name);
        if (logger == null) {
            logger = new Logger(name);
            map.put(name, logger);
        }
        return logger;
    }

    public void d(String msg) {
        Log.d(name, msg);
    }

    public void d(String msg, Object... format) {
        Log.d(name, String.format(msg, format));
    }

    public void i(String msg) {
        Log.i(name, msg);
    }

    public void i(String msg, Object... format) {
        Log.i(name, String.format(msg, format));
    }

    public void v(String msg) {
        Log.v(name, msg);
    }

    public void v(String msg, Object... format) {
        Log.v(name, String.format(msg, format));
    }

    public void w(String msg) {
        Log.w(name, msg);
    }


    public void w(String msg, Object... format) {
        Log.w(name, String.format(msg, format));
    }

    public void w(String msg, Throwable e) {
        Log.w(name, msg, e);
    }

    public void w(String msg, Throwable e, Object... format) {
        Log.w(name, String.format(msg, format), e);
    }

    public void e(String msg) {
        Log.e(name, msg);
    }

    public void e(String msg, Object... format) {
        Log.e(name, String.format(msg, format));
    }


    public void e(String msg, Throwable e) {
        Log.e(name, msg, e);
    }

    public void e(String msg, Throwable e, Object... format) {
        Log.e(name, String.format(msg, format), e);
    }


}
