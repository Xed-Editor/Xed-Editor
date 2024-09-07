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
package io.github.rosemoe.sora.lang.analysis;

import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.github.rosemoe.sora.lang.styling.CodeBlock;
import io.github.rosemoe.sora.lang.styling.Span;
import io.github.rosemoe.sora.lang.styling.SpanFactory;
import io.github.rosemoe.sora.lang.styling.Spans;
import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.lang.util.BaseAnalyzeManager;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Asynchronous base implementation of {@link IncrementalAnalyzeManager}
 * <p>
 * {@inheritDoc}
 *
 * @author Rosemoe
 */
public abstract class AsyncIncrementalAnalyzeManager<S, T> extends BaseAnalyzeManager implements IncrementalAnalyzeManager<S, T> {

    private final static int MSG_BASE = 11451400;
    private final static int MSG_INIT = MSG_BASE + 1;
    private final static int MSG_MOD = MSG_BASE + 2;
    private static int sThreadId = 0;
    private LooperThread thread;
    private volatile long runCount;

    private synchronized static int nextThreadId() {
        sThreadId++;
        return sThreadId;
    }

    /**
     * Run the given code block only when the receiver is currently non-null
     */
    protected void withReceiver(@NonNull ReceiverConsumer consumer) {
        var r = getReceiver();
        if (r != null) {
            consumer.accept(r);
        }
    }

    @Override
    public void insert(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence insertedText) {
        if (thread != null) {
            increaseRunCount();
            thread.offerMessage(MSG_MOD, new TextModification(IntPair.pack(start.line, start.column), IntPair.pack(end.line, end.column), insertedText));
        }
    }

    @Override
    public void delete(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence deletedText) {
        if (thread != null) {
            increaseRunCount();
            thread.offerMessage(MSG_MOD, new TextModification(IntPair.pack(start.line, start.column), IntPair.pack(end.line, end.column), null));
        }
    }

    @Override
    public void rerun() {
        if (thread != null) {
            if (thread.isAlive()) {
                thread.interrupt();
                thread.abort = true;
            }
            thread = null;
        }
        var ref = getContentRef();
        if (ref != null) {
            final var text = ref.getReference().copyText(false);
            text.setUndoEnabled(false);
            thread = new LooperThread();
            thread.setName("AsyncAnalyzer-" + nextThreadId());
            thread.offerMessage(MSG_INIT, text);
            increaseRunCount();
            sendNewStyles(null);
            thread.start();
        }
    }

    @Override
    public LineTokenizeResult<S, T> getState(int line) {
        final var thread = this.thread;
        if (thread == Thread.currentThread()) {
            if (line >= 0 && line < thread.states.size()) {
                return thread.states.get(line);
            }
            return null;
        }
        throw new SecurityException("Can not get state from non-analytical or abandoned thread");
    }

    @Override
    public void onAbandonState(S state) {

    }

    @Override
    public void onAddState(S state) {

    }

    private synchronized void increaseRunCount() {
        runCount++;
    }

    @Override
    public void destroy() {
        if (thread != null) {
            if (thread.isAlive()) {
                thread.interrupt();
            }
            thread.abort = true;
        }
        thread = null;
        super.destroy();
    }

    private void sendNewStyles(Styles styles) {
        final var r = getReceiver();
        if (r != null) {
            r.setStyles(this, styles);
        }
    }

    private void sendUpdate(Styles styles, int startLine, int endLine) {
        final var r = getReceiver();
        if (r != null) {
            r.updateStyles(this, styles, new SequenceUpdateRange(startLine, endLine));
        }
    }

    /**
     * Compute code blocks
     *
     * @param text The text. can be safely accessed.
     */
    public abstract List<CodeBlock> computeBlocks(Content text, CodeBlockAnalyzeDelegate delegate);

    public Styles getManagedStyles() {
        var thread = Thread.currentThread();
        if (thread.getClass() != AsyncIncrementalAnalyzeManager.LooperThread.class) {
            throw new IllegalThreadStateException();
        }
        return ((AsyncIncrementalAnalyzeManager<?, ?>.LooperThread) thread).styles;
    }

    private static class LockedSpans implements Spans {

        private static final String LOG_TAG = "LockedSpans";

        private final Lock lock;
        private final List<Line> lines;

        public LockedSpans() {
            lines = new ArrayList<>(128);
            lock = new ReentrantLock();
        }

        @Override
        public void adjustOnDelete(CharPosition start, CharPosition end) {

        }

        @Override
        public void adjustOnInsert(CharPosition start, CharPosition end) {

        }

        @Override
        public int getLineCount() {
            return lines.size();
        }

        @Override
        public Reader read() {
            return new ReaderImpl();
        }

        @Override
        public Modifier modify() {
            return new ModifierImpl();
        }

        @Override
        public boolean supportsModify() {
            return true;
        }

        private static class Line {

            public Lock lock = new ReentrantLock();

            public List<Span> spans;

            public Line(List<Span> s) {
                spans = s;
            }

        }

        private class ReaderImpl implements Spans.Reader {

            private Line line;

            public void moveToLine(int line) {
                if (line < 0 || line >= lines.size()) {
                    if (this.line != null) {
                        this.line.lock.unlock();
                    }
                    this.line = null;
                } else {
                    if (this.line != null) {
                        this.line.lock.unlock();
                    }
                    var locked = false;
                    try {
                        locked = lock.tryLock(100, TimeUnit.MICROSECONDS);
                    } catch (InterruptedException e) {
                        Log.w(LOG_TAG, "failed to acquire the lock", e);
                        Thread.currentThread().interrupt();
                    }
                    if (locked) {
                        try {
                            var obj = lines.get(line);
                            if (obj.lock.tryLock()) {
                                this.line = obj;
                            } else {
                                this.line = null;
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else {
                        this.line = null;
                    }
                }
            }

            @Override
            public int getSpanCount() {
                return line == null ? 1 : line.spans.size();
            }

            @Override
            public Span getSpanAt(int index) {
                return line == null ? SpanFactory.obtain(0, EditorColorScheme.TEXT_NORMAL) : line.spans.get(index);
            }

            @Override
            public List<Span> getSpansOnLine(int line) {
                var spans = new ArrayList<Span>();
                var locked = false;
                try {
                    locked = lock.tryLock(1, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.w(LOG_TAG, "failed to acquire the lock", e);
                }
                if (locked) {
                    Line obj = null;
                    try {
                        if (line < lines.size()) {
                            obj = lines.get(line);
                        }
                    } finally {
                        lock.unlock();
                    }
                    if (obj != null && obj.lock.tryLock()) {
                        try {
                            return Collections.unmodifiableList(obj.spans);
                        } finally {
                            obj.lock.unlock();
                        }
                    } else {
                        spans.add(getSpanAt(0));
                    }
                } else {
                    spans.add(getSpanAt(0));
                }
                return spans;
            }
        }

        private class ModifierImpl implements Modifier {

            @Override
            public void setSpansOnLine(int line, List<Span> spans) {
                lock.lock();
                try {
                    while (lines.size() <= line) {
                        var list = new ArrayList<Span>();
                        list.add(SpanFactory.obtain(0, EditorColorScheme.TEXT_NORMAL));
                        lines.add(new Line(list));
                    }
                    var obj = lines.get(line);
                    obj.lock.lock();
                    try {
                        obj.spans = spans;
                    } finally {
                        obj.lock.unlock();
                    }
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void addLineAt(int line, List<Span> spans) {
                lock.lock();
                try {
                    lines.add(line, new Line(spans));
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void deleteLineAt(int line) {
                lock.lock();
                try {
                    var obj = lines.get(line);
                    obj.lock.lock();
                    try {
                        lines.remove(line);
                    } finally {
                        obj.lock.unlock();
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

    }

    private static class TextModification {

        private final long start;
        private final long end;
        /**
         * null for deletion
         */
        private final CharSequence changedText;

        TextModification(long start, long end, CharSequence text) {
            this.start = start;
            this.end = end;
            changedText = text;
        }
    }

    /**
     * Helper class for analyzing code block
     */
    public class CodeBlockAnalyzeDelegate {

        private final LooperThread thread;
        int suppressSwitch;

        CodeBlockAnalyzeDelegate(@NonNull LooperThread lp) {
            thread = lp;
        }

        public void setSuppressSwitch(int suppressSwitch) {
            this.suppressSwitch = suppressSwitch;
        }

        void reset() {
            suppressSwitch = Integer.MAX_VALUE;
        }

        public boolean isCancelled() {
            return thread.myRunCount != runCount || thread.abort || thread.isInterrupted();
        }

        public boolean isNotCancelled() {
            return !isCancelled();
        }

    }

    private final class LooperThread extends Thread {

        private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
        volatile boolean abort;
        Content shadowed;
        long myRunCount;

        List<LineTokenizeResult<S, T>> states = new ArrayList<>();
        Styles styles;
        LockedSpans spans;
        CodeBlockAnalyzeDelegate delegate = new CodeBlockAnalyzeDelegate(this);

        public void offerMessage(int what, @Nullable Object obj) {
            var msg = Message.obtain();
            msg.what = what;
            msg.obj = obj;
            offerMessage(msg);
        }

        public void offerMessage(@NonNull Message msg) {
            // Result ignored: capacity is enough as it is INT_MAX
            //noinspection ResultOfMethodCallIgnored
            messageQueue.offer(msg);
        }

        private void initialize() {
            styles = new Styles(spans = new LockedSpans());
            S state = getInitialState();
            var mdf = spans.modify();
            for (int i = 0; i < shadowed.getLineCount() && !abort && !isInterrupted(); i++) {
                var line = shadowed.getLine(i);
                var result = tokenizeLine(line, state, i);
                state = result.state;
                var spans = result.spans != null ? result.spans : generateSpansForLine(result);
                states.add(result.clearSpans());
                onAddState(result.state);
                mdf.addLineAt(i, spans);
            }
            styles.blocks = computeBlocks(shadowed, delegate);
            styles.setSuppressSwitch(delegate.suppressSwitch);
            styles.finishBuilding();
            if (!abort)
                sendNewStyles(styles);
        }

        public boolean handleMessage(@NonNull Message msg) {
            try {
                myRunCount = runCount;
                delegate.reset();
                switch (msg.what) {
                    case MSG_INIT:
                        shadowed = (Content) msg.obj;
                        if (!abort && !isInterrupted()) {
                            initialize();
                        }
                        break;
                    case MSG_MOD:
                        int updateStart = 0, updateEnd = 0;
                        if (!abort && !isInterrupted()) {
                            var mod = (TextModification) msg.obj;
                            int startLine = IntPair.getFirst(mod.start);
                            int endLine = IntPair.getFirst(mod.end);

                            updateStart = startLine;
                            if (mod.changedText == null) {
                                shadowed.delete(IntPair.getFirst(mod.start), IntPair.getSecond(mod.start),
                                        IntPair.getFirst(mod.end), IntPair.getSecond(mod.end));
                                S state = startLine == 0 ? getInitialState() : states.get(startLine - 1).state;
                                // Remove states
                                if (endLine >= startLine + 1) {
                                    var subList = states.subList(startLine + 1, endLine + 1);
                                    for (LineTokenizeResult<S, T> stLineTokenizeResult : subList) {
                                        onAbandonState(stLineTokenizeResult.state);
                                    }
                                    subList.clear();
                                }
                                var mdf = spans.modify();
                                for (int i = startLine + 1; i <= endLine; i++) {
                                    mdf.deleteLineAt(startLine + 1);
                                }
                                int line = startLine;
                                while (line < shadowed.getLineCount()) {
                                    var res = tokenizeLine(shadowed.getLine(line), state, line);
                                    mdf.setSpansOnLine(line, res.spans != null ? res.spans : generateSpansForLine(res));
                                    var old = states.set(line, res.clearSpans());
                                    if (old != null) {
                                        onAbandonState(old.state);
                                    }
                                    onAddState(res.state);
                                    if (stateEquals(old == null ? null : old.state, res.state)) {
                                        break;
                                    }
                                    state = res.state;
                                    line++;
                                }
                                updateEnd = line;
                            } else {
                                shadowed.insert(IntPair.getFirst(mod.start), IntPair.getSecond(mod.start), mod.changedText);
                                S state = startLine == 0 ? getInitialState() : states.get(startLine - 1).state;
                                int line = startLine;
                                var spans = styles.spans.modify();
                                // Add Lines
                                while (line <= endLine) {
                                    var res = tokenizeLine(shadowed.getLine(line), state, line);
                                    if (line == startLine) {
                                        spans.setSpansOnLine(line, res.spans != null ? res.spans : generateSpansForLine(res));
                                        var old = states.set(line, res.clearSpans());
                                        if (old != null) {
                                            onAbandonState(old.state);
                                        }
                                    } else {
                                        spans.addLineAt(line, res.spans != null ? res.spans : generateSpansForLine(res));
                                        states.add(line, res.clearSpans());
                                    }
                                    onAddState(res.state);
                                    state = res.state;
                                    line++;
                                }
                                // line = end.line + 1, check whether the state equals
                                boolean flag = true;
                                while (line < shadowed.getLineCount() && flag) {
                                    var res = tokenizeLine(shadowed.getLine(line), state, line);
                                    if (stateEquals(res.state, states.get(line).state)) {
                                        flag = false;
                                    }
                                    spans.setSpansOnLine(line, res.spans != null ? res.spans : generateSpansForLine(res));
                                    var old = states.set(line, res.clearSpans());
                                    if (old != null) {
                                        onAbandonState(old.state);
                                    }
                                    onAddState(res.state);
                                    state = res.state;
                                    line++;
                                }
                                updateEnd = line;
                            }
                        }
                        // Do not update incomplete code blocks
                        var blocks = computeBlocks(shadowed, delegate);
                        if (delegate.isNotCancelled()) {
                            styles.blocks = blocks;
                            styles.finishBuilding();
                            styles.setSuppressSwitch(delegate.suppressSwitch);
                        }
                        if (!abort) {
                            sendUpdate(styles, updateStart, updateEnd);
                        }
                        break;
                }
                return true;
            } catch (Exception e) {
                Log.w("AsyncAnalysis", "Thread " + Thread.currentThread().getName() + " failed", e);
            }
            return false;
        }

        @Override
        public void run() {
            try {
                while (!abort && !isInterrupted()) {
                    var msg = messageQueue.take();
                    if (!handleMessage(msg)) {
                        break;
                    }
                    msg.recycle();
                }
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }


    public interface ReceiverConsumer {

        void accept(@NonNull StyleReceiver receiver);

    }


}
