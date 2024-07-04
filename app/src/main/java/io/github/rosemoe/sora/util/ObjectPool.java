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

public abstract class ObjectPool<T> {

    private final Object[] pool;

    public ObjectPool() {
        this(16);
    }

    public ObjectPool(int size) {
        pool = new Object[size];
    }

    public void recycle(T obj) {
        if (obj == null)
            return;
        onRecycleObject(obj);
        synchronized (this) {
            for (int i = 0; i < pool.length; i++) {
                if (pool[i] == null) {
                    pool[i] = obj;
                    break;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public T obtain() {
        T result = null;
        synchronized (this) {
            for (int i = pool.length - 1; i >= 0; i--) {
                if (pool[i] != null) {
                    result = (T) pool[i];
                    pool[i] = null;
                    break;
                }
            }
        }
        if (result == null) {
            result = allocateNew();
        }
        return result;
    }

    protected void onRecycleObject(T recycledObj) {

    }

    protected abstract T allocateNew();

}
