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

/**
 * ArrayList for primitive type long
 *
 * @author Rosemoe
 */
public class LongArrayList {

    private long[] data;
    private int length;

    public LongArrayList() {
        data = new long[64];
    }

    /**
     * Add a value at end
     */
    public void add(long value) {
        data[length++] = value;
        if (data.length == length) {
            long[] newData = new long[length << 1];
            System.arraycopy(data, 0, newData, 0, length);
            data = newData;
        }
    }

    /**
     * Get length of the list
     */
    public int size() {
        return length;
    }

    /**
     * Set element at given index to {@code value}
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    public void set(int index, long value) {
        if (index >= length || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        data[index] = value;
    }

    /**
     * Refers to C++ algorithm lower_bound().
     * Compare by {@link IntPair#getFirst(long)} on each element.
     * <p>
     * Note that, you guarantee the sequence in list is in ascendant order.
     *
     * @param key Target value
     * @return Index of target value, or index of the insertion point (that's the index of first element
     * bigger than {@code key} or array length)
     */
    public int lowerBoundByFirst(int key) {
        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = IntPair.getFirst(data[mid]);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return low;  // key not found.
    }

    public int lowerBound(long key) {
        int low = 0;
        int high = length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            long midVal = data[mid];

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }
        return low;  // key not found.
    }

    /**
     * Get element at given index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    public long get(int index) {
        if (index >= length || index < 0) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        return data[index];
    }

    public void clear() {
        length = 0;
    }

}
