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

import static io.github.rosemoe.sora.util.IntPair.getFirst;
import static io.github.rosemoe.sora.util.IntPair.getSecond;
import static io.github.rosemoe.sora.util.IntPair.pack;

import android.util.SparseIntArray;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A general implementation of big root heap
 *
 * @author Rosemoe
 */
public class BinaryHeap {

    /**
     * Lock for multi-thread reusing
     */
    public final Lock lock = new ReentrantLock();
    /**
     * Map from id to its position in heap array
     */
    private final SparseIntArray idToPosition;
    /**
     * Id allocator
     */
    private int idAllocator = 1;
    /**
     * Current node count in heap
     */
    private int nodeCount;
    /**
     * Node array for heap.
     * first:  id
     * second: data
     */
    private long[] nodes;

    /**
     * Create a binary heap
     * This heap maintains its max value in heap
     */
    public BinaryHeap() {
        idToPosition = new SparseIntArray();
        nodeCount = 0;
        nodes = new long[129];
    }

    private static int id(long value) {
        return getFirst(value);
    }

    private static int data(long value) {
        return getSecond(value);
    }

    /**
     * Clear all the nodes in the heap
     */
    public void clear() {
        nodeCount = 0;
        idToPosition.clear();
        idAllocator = -1;
    }

    /**
     * Ensure there is enough space
     *
     * @param capacity desired space size
     */
    public void ensureCapacity(int capacity) {
        capacity++;
        if (nodes.length < capacity) {
            var origin = nodes;
            if (nodes.length << 1 >= capacity) {
                nodes = new long[nodes.length << 1];
            } else {
                nodes = new long[capacity];
            }
            System.arraycopy(origin, 0, nodes, 0, nodeCount + 1);
        }
    }

    /**
     * Get the max value in this heap or zero if no node is in heap
     *
     * @return Max value
     */
    public int top() {
        if (nodeCount == 0) {
            return 0;
        }
        return data(nodes[1]);
    }

    /**
     * Get total node count in heap
     */
    public int getNodeCount() {
        return nodeCount;
    }

    /**
     * Internal implementation to move down nodes
     *
     * @param position target node's position in heap
     */
    private void heapifyDown(int position) {
        for (int child = position * 2; child <= nodeCount; child = position * 2) {
            long parentNode = nodes[position], childNode;
            if (child + 1 <= nodeCount && data(nodes[child + 1]) > data(nodes[child])) {
                child = child + 1;
            }
            childNode = nodes[child];
            if (data(parentNode) < data(childNode)) {
                idToPosition.put(id(childNode), position);
                idToPosition.put(id(parentNode), child);
                nodes[child] = parentNode;
                nodes[position] = childNode;
                position = child;
            } else {
                break;
            }
        }
    }

    /**
     * Internal implementation to move up nodes
     *
     * @param position target node's position in heap
     */
    private void heapifyUp(int position) {
        for (int parent = position / 2; parent >= 1; parent = position / 2) {
            long childNode = nodes[position], parentNode = nodes[parent];
            if (data(childNode) > data(parentNode)) {
                idToPosition.put(id(childNode), parent);
                idToPosition.put(id(parentNode), position);
                nodes[position] = parentNode;
                nodes[parent] = childNode;
                position = parent;
            } else {
                break;
            }
        }
    }

    /**
     * Add a new node to the heap
     *
     * @return ID of node
     * @throws IllegalStateException when there is no new id available
     */
    public int push(int value) {
        ensureCapacity(nodeCount + 1);
        if (idAllocator == Integer.MAX_VALUE) {
            throw new IllegalStateException("unable to allocate more id");
        }
        int id = idAllocator++;
        nodeCount++;
        nodes[nodeCount] = pack(id, value);
        idToPosition.put(id, nodeCount);
        heapifyUp(nodeCount);
        return id;
    }

    /**
     * Update the value of node with given id to newValue
     *
     * @param id       ID returned by push()
     * @param newValue new value for this node
     * @throws IllegalArgumentException when the id is invalid
     */
    public void update(int id, int newValue) {
        int position = idToPosition.get(id, 0);
        if (position == 0) {
            throw new IllegalArgumentException("trying to update with an invalid id");
        }
        int origin = data(nodes[position]);
        nodes[position] = pack(id(nodes[position]), newValue);
        if (origin < newValue) {
            heapifyUp(position);
        } else if (origin > newValue) {
            heapifyDown(position);
        }
    }

    /**
     * Remove node with given id
     *
     * @param id ID returned by push()
     * @throws IllegalArgumentException when the id is invalid
     */
    public void remove(int id) {
        int position = idToPosition.get(id, 0);
        if (position == 0) {
            throw new IllegalArgumentException("trying to remove with an invalid id");
        }
        idToPosition.delete(id);
        //Replace removed node with last node
        nodes[position] = nodes[nodeCount];
        //Release node
        nodes[nodeCount--] = 0;
        //Do not update heap if it is just the last node
        if (position == nodeCount + 1) {
            return;
        }
        idToPosition.put(id(nodes[position]), position);
        heapifyUp(position);
        heapifyDown(position);
    }

}
