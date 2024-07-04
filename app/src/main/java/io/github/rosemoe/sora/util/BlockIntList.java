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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class BlockIntList {

    private final static int CACHE_COUNT = 8;
    private final static int CACHE_SWITCH = 30;
    public final Lock lock = new ReentrantLock();
    private final int blockSize;
    private final List<Block> recycled = new java.util.ArrayList<>();
    private final List<Cache> caches;
    private int length;
    private int modCount;
    private Block head;
    private int foundIndex;
    private Block foundBlock;
    private int updateTime;
    private int max;

    public BlockIntList() {
        this(1000);
    }

    public BlockIntList(int blockSize) {
        this.blockSize = blockSize;
        if (blockSize <= 4) {
            throw new IllegalArgumentException("block size must be bigger than 4");
        }
        length = 0;
        modCount = 0;
        head = new Block();
        caches = new ArrayList<>(CACHE_COUNT + 2);
    }

    public int getMax() {
        if (modCount != updateTime) {
            updateTime = modCount;
        }
        computeMax();
        return max;
    }

    private void computeMax() {
        max = 0;
        var block = head;
        while (block != null) {
            max = Math.max(max, block.max);
            block = block.next;
        }
    }

    /**
     * 0 <=index < length
     */
    private void findBlock1(int index) {
        int distance = index;
        int usedNo = -1;
        Block fromBlock = head;
        for (int i = 0; i < caches.size(); i++) {
            Cache c = caches.get(i);
            if (c.indexOfStart < index && (index - c.indexOfStart) < distance) {
                distance = index - c.indexOfStart;
                fromBlock = c.block;
                usedNo = i;
            }
        }
        if (usedNo != -1) {
            Collections.swap(caches, 0, usedNo);
        }
        int crossCount = 0;
        while (distance >= fromBlock.size()) {
            if (fromBlock.next != null) {
                distance -= fromBlock.size();
                fromBlock = fromBlock.next;
            } else {
                break;
            }
            crossCount++;
        }
        if (crossCount >= CACHE_SWITCH) {
            caches.add(cache(index - distance, fromBlock));
        }
        if (caches.size() > CACHE_COUNT) {
            caches.remove(caches.size() - 1);
        }
        foundIndex = distance;
        foundBlock = fromBlock;
    }

    private void invalidateCacheFrom(int index) {
        for (int i = 0; i < caches.size(); i++) {
            if (caches.get(i).indexOfStart >= index) {
                caches.remove(i);
                i--;
            }
        }
    }

    private Block newBlock() {
        if (recycled.isEmpty()) {
            return new Block();
        }
        return recycled.remove(recycled.size() - 1);
    }

    public void add(int element) {
        add(length, element);
    }

    public void add(int index, int element) {
        if (index < 0 || index > size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        invalidateCacheFrom(index);
        // Find the block
        var block = foundBlock;
        index = foundIndex;
        while (index > block.size()) {
            if (block.next == null) {
                // No next block available
                // Add element to this block directly and separate later
                break;
            } else {
                // Go to next block
                index -= block.size();
                block = block.next;
            }
        }
        // Add
        block.add(index, element);
        length++;
        // Separate if required
        if (block.size() > blockSize) {
            block.separate();
        }
        modCount++;
    }

    public int remove(int index) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        int backup = index;
        // Find the block
        Block previous = null;
        Block block = head;
        while (index >= block.size()) {
            // Go to next block
            index -= block.size();
            previous = block;
            block = block.next;
        }
        // Remove
        int removedValue = block.remove(index);
        invalidateCacheFrom(backup - index);
        // Delete blank block
        if (block.size() == 0 && previous != null) {
            previous.next = block.next;
            recycled.add(block);
        } else if (block.size() < blockSize / 4 && previous != null && previous.size() + block.size() < blockSize / 2) {
            // Merge small pieces
            previous.next = block.next;
            System.arraycopy(block.data, 0, previous.data, previous.size, block.size);
            previous.size += block.size;
        }
        modCount++;
        length--;
        return removedValue;
    }

    public int set(int index, int element) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        var old = foundBlock.set(foundIndex, element);
        modCount++;
        return old;
    }

    public int get(int index) {
        if (index < 0 || index >= size()) {
            throw new ArrayIndexOutOfBoundsException("index = " + index + ", length = " + size());
        }
        findBlock1(index);
        return (int) foundBlock.get(foundIndex);
    }

    public void removeRange(int fromIndex, int toIndex) {
        if (toIndex > length || fromIndex < 0 || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException();
        }
        // Find the block
        Block previous = null;
        Block block = head;
        while (fromIndex >= block.size()) {
            // Go to next block
            fromIndex -= block.size();
            toIndex -= block.size();
            previous = block;
            block = block.next;
        }
        int deleteLength = toIndex - fromIndex;
        int begin = fromIndex;
        while (deleteLength > 0) {
            if (begin == 0 && deleteLength >= block.size()) {
                // Covers whole region
                if (previous != null) {
                    previous.next = block.next;
                    recycled.add(block);
                }
                deleteLength -= block.size();
                block.size = 0;
                block = block.next;
                continue;
            }
            begin = 0;
            int end = Math.min(block.size(), begin + deleteLength);
            block.remove(begin, end);
            deleteLength -= (end - begin);
            previous = block;
            block = block.next;
        }
        length -= (toIndex - fromIndex);
    }

    public void clear() {
        head = new Block();
        length = 0;
        caches.clear();
        foundBlock = null;
        foundIndex = 0;
    }

    public int size() {
        return length;
    }

    private Cache cache(int index, Block block) {
        Cache c = new Cache();
        c.indexOfStart = index;
        c.block = block;
        return c;
    }

    private class Block {

        private final int[] data;
        private int size;
        private int max;
        private Block next;

        public Block() {
            data = new int[blockSize + 5];
            size = 0;
        }

        public int size() {
            return size;
        }

        public void add(int index, int element) {
            // Shift after
            System.arraycopy(data, index, data, index + 1, size - index);
            // Add
            data[index] = element;
            size++;
            if (element > max) {
                max = element;
            }
        }

        public int set(int index, int element) {
            int old = data[index];
            data[index] = element;
            if (old == max) {
                if (element >= old) {
                    max = element;
                } else {
                    compute();
                }
            } else if (element > max) {
                max = element;
            }
            return old;
        }

        public int get(int index) {
            return data[index];
        }

        public int remove(int index) {
            var oldValue = data[index];
            System.arraycopy(data, index + 1, data, index, size - index - 1);
            size--;
            if (oldValue == max) {
                compute();
            }
            return oldValue;
        }

        public void remove(int start, int end) {
            System.arraycopy(data, end, data, start, size - end);
            size -= (end - start);
            compute();
        }

        public void separate() {
            Block oldNext = this.next;
            Block newNext = newBlock();
            final int divPoint = blockSize * 3 / 4;
            System.arraycopy(this.data, divPoint, newNext.data, 0, this.size - divPoint);
            newNext.size = this.size - divPoint;
            this.size = divPoint;
            this.next = newNext;
            newNext.next = oldNext;
        }

        private void compute() {
            max = 0;
            for (int i = 0; i < size; i++) {
                max = Math.max(max, data[i]);
            }
        }
    }

    private class Cache {
        public Block block;
        public int indexOfStart;
    }


}