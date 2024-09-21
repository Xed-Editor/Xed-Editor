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

import androidx.annotation.NonNull;

import java.util.AbstractList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SegmentList<T> extends AbstractList<T> {

    public final static int DEFAULT_SEGMENT_CAPACITY = 8192;

    private final List<Segment<T>> segments;

    private final int segmentCapacity;

    private int length;

    public SegmentList() {
        this(DEFAULT_SEGMENT_CAPACITY);
    }

    public SegmentList(int segmentCapacity) {
        if (segmentCapacity < 4) {
            throw new IllegalArgumentException("block size should be at least 4");
        }
        this.segmentCapacity = segmentCapacity;
        segments = new ArrayList<>();
    }

    private void checkInsertIndex(int index) {
        if (index < 0 || index > length) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds. length = " + length);
        }
    }

    private void checkAccessIndex(int index) {
        if (index < 0 || index >= length) {
            throw new IndexOutOfBoundsException("index " + index + " out of bounds. length = " + length);
        }
    }

    private static class FindResult<T> {

        Segment<T> segment;
        int offset;
        int blockIndex;

        public FindResult() {

        }

        public FindResult(Segment<T> segment, int offset, int segIndex) {
            this.segment = segment;
            this.offset = offset;
            this.blockIndex = segIndex;
        }
    }

    private FindResult<T> result = new FindResult<>();

    private FindResult<T> makeResult(Segment<T> segment, int offset, int segIndex) {
        result.blockIndex = segIndex;
        result.segment = segment;
        result.offset = offset;
        return result;
    }

    private FindResult<T> getSegment(int index) {
        if (segments.isEmpty()) {
            segments.add(new Segment<>(segmentCapacity));
        }
        int offset = 0;
        var backBlock = segments.get(segments.size() - 1);
        int backOffset = size() - backBlock.size();
        for (int i = 0, j = segments.size() - 1; i <= j; i++, j--) {
            var block = segments.get(i);
            if ((index >= offset && index < offset + block.size()) || i + 1 == segments.size()) {
                return makeResult(block, offset, i);
            }
            offset += block.size();

            block = backBlock;
            if ((index >= backOffset && index < backOffset + block.size()) || (j == segments.size() - 1 && index == length)) {
                return makeResult(block, backOffset, j);
            }
            if (j > 0) {
                backBlock = segments.get(j - 1);
                backOffset -= backBlock.size();
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private FindResult<T> getSegmentMut(int index) {
        var res = getSegment(index);
        res.segment = ensureMutable(res.blockIndex);
        return res;
    }

    private Segment<T> ensureMutable(int segIdx) {
        var block = segments.get(segIdx);
        var n = block.toMutable();
        if (block != n) {
            segments.set(segIdx, n);
            block.release();
            return n;
        }
        return block;
    }

    @Override
    public T set(int index, T element) {
        checkAccessIndex(index);
        var result = getSegmentMut(index);
        return result.segment.set(index - result.offset, element);
    }

    @Override
    public void add(int index, T element) {
        checkInsertIndex(index);
        var result = getSegmentMut(index);
        result.segment.add(index - result.offset, element);
        length++;
        adjustElements(result.blockIndex, result.segment);
        if (result.segment.size() >= segmentCapacity) {
            var divPoint = segmentCapacity / 2;
            var seg = new Segment<T>(segmentCapacity);
            seg.addAll(result.segment.subList(divPoint, result.segment.size()));
            result.segment.removeRange(divPoint, result.segment.size());
            segments.add(result.blockIndex + 1, seg);
        }
    }

    @Override
    public T remove(int index) {
        checkAccessIndex(index);
        var result = getSegmentMut(index);
        var res = result.segment.remove(index - result.offset);
        length--;
        mergeSegment(index - 1, index);
        mergeSegment(index, index + 1);
        return res;
    }

    @Override
    public T get(int index) {
        checkAccessIndex(index);
        var result = getSegment(index);
        return result.segment.get(index - result.offset);
    }

    @Override
    protected void removeRange(int fromIndex, int toIndex) {
        if (fromIndex > toIndex) throw new IndexOutOfBoundsException("start > end");
        if (fromIndex < 0 || toIndex > length)
            throw new IndexOutOfBoundsException("start = " + fromIndex + ", end = " + toIndex + ", length = " + size());
        if (fromIndex == toIndex) return;
        var res = getSegment(fromIndex);
        int offset = res.offset;
        int index = res.blockIndex;
        var seg = res.segment;

        while (toIndex - offset > 0 && index < segments.size()) {
            int segLength = seg.size();
            if (fromIndex <= offset && toIndex >= offset + segLength) {
                // Remove the segment
                segments.remove(index);
                seg.release();
            } else {
                ensureMutable(index);
                seg = segments.get(index);
                var sub = seg.subList(Math.max(fromIndex - offset, 0), Math.min(toIndex - offset, segLength));
                sub.clear();
                index++;
            }
            offset += segLength;
            if (index < segments.size())
                seg = segments.get(index);
        }
        mergeSegment(index - 1, index);
        length -= toIndex - fromIndex;
    }

    @Override
    public int size() {
        return length;
    }

    @Override
    public void clear() {
        for (var seg : segments) {
            seg.release();
        }
        segments.clear();
        length = 0;
    }

    public SegmentList<T> shallowCopy() {
        var list = new SegmentList<T>(segmentCapacity);
        list.segments.clear();
        for (var seg : segments) {
            seg.retain();
        }
        list.segments.addAll(segments);
        list.length = length;
        return list;
    }

    private void mergeSegment(int seg1, int seg2) {
        if (seg1 > seg2) {
            int tmp = seg1;
            seg1 = seg2;
            seg2 = tmp;
        }
        if (seg1 == seg2 || seg1 < 0 || seg2 >= segments.size()) return;
        var pre = segments.get(seg1);
        var aft = segments.get(seg2);
        if (pre.size() + aft.size() <= segmentCapacity * 3 / 4) {
            ensureMutable(seg1);
            pre = segments.get(seg1);
            pre.addAll(aft);
            segments.remove(seg2);
            aft.release();
        }
    }

    private void adjustElements(int segIdx, Segment<T> mutCur) {
        if (segIdx > 0) {
            var pre = segments.get(segIdx - 1);
            if (pre.isMutable() && pre.size() <= segmentCapacity * 4 / 5 && mutCur.size() > segmentCapacity * 4 / 5) {
                var sub = mutCur.subList(0, segmentCapacity * 4 / 5 - pre.size());
                pre.addAll(sub);
                sub.clear();
            }
        }
    }

    public void forEachCompat(@NonNull ConsumerCompat<T> consumer) {
        for (int i = 0; i < segments.size(); i++) {
            var seg = segments.get(i);
            for (int i1 = 0; i1 < seg.size(); i1++) {
                consumer.accept(seg.get(i1));
            }
        }
    }

    public interface ConsumerCompat<T> {
        void accept(T obj);
    }

    private static class Segment<T> extends ArrayList<T> implements ShareableData<Segment<T>> {

        public Segment() {

        }

        public Segment(int initialCapacity) {
            super(initialCapacity);
        }

        private final AtomicInteger refCount = new AtomicInteger(1);

        @Override
        public void retain() {
            refCount.incrementAndGet();
        }

        @Override
        public void release() {
            if (refCount.decrementAndGet() < 0) {
                throw new IllegalStateException("illegal release invocation");
            }
        }

        @Override
        public boolean isMutable() {
            return refCount.get() == 1;
        }

        @Override
        public Segment<T> toMutable() {
            if (isMutable()) {
                return this;
            } else {
                return copy();
            }
        }

        public Segment<T> copy() {
            var res = new Segment<T>(this.size());
            res.addAll(this);
            return res;
        }
    }

}
