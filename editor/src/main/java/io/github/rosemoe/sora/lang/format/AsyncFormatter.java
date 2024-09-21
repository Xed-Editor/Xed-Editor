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
package io.github.rosemoe.sora.lang.format;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.lang.ref.WeakReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;

/**
 * Base class for formatting code in another thread.
 */
public abstract class AsyncFormatter implements Formatter {

    private final static String LOG_TAG = "AsyncFormatter";
    private static int sThreadId = 0;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private WeakReference<FormatResultReceiver> receiver;
    private volatile Content text;
    private volatile TextRange range;
    private volatile TextRange cursorRange;

    private FormattingThread thread;

    private synchronized static int nextThreadId() {
        sThreadId++;
        return sThreadId;
    }

    @Override
    public void setReceiver(FormatResultReceiver receiver) {
        if (receiver == null) {
            this.receiver = null;
            return;
        }
        this.receiver = new WeakReference<>(receiver);
    }

    private void run() {
        if (thread == null || !thread.isAlive()) {
            // Create new thread
            Log.v(LOG_TAG, "Starting a new thread for formatting");
            thread = new FormattingThread();
            thread.setDaemon(true);
            thread.setName("AsyncFormatter-" + nextThreadId());
            thread.start();
        } else {
            // Wake up thread
            Log.v(LOG_TAG, "Waking up thread for formatting");
            lock.lock();
            condition.signal();
            lock.unlock();
        }
    }

    @Override
    public void format(@NonNull Content text, @NonNull TextRange cursorRange) {
        this.text = text;
        range = null;
        this.cursorRange = cursorRange;
        run();
    }

    @Override
    public boolean isRunning() {
        return thread != null && thread.isAlive() && lock.isLocked();
    }

    @Override
    public void formatRegion(@NonNull Content text, @NonNull TextRange rangeToFormat, @NonNull TextRange cursorRange) {
        this.text = text;
        range = rangeToFormat;
        this.cursorRange = cursorRange;
        run();
    }

    /**
     * like {@link Formatter#format(Content, TextRange)}, but run in background thread.
     * <p>
     * Implementation of this method can edit text directly to generate formatted code.
     *
     * @return the new cursor range to be applied to the text
     */
    @WorkerThread
    @Nullable
    public abstract TextRange formatAsync(@NonNull Content text, @NonNull TextRange cursorRange);

    /**
     * like {@link Formatter#formatRegion(Content, TextRange, TextRange)}, but run in background thread
     * <p>
     * Implementation of this method can edit text directly to generate formatted code.
     *
     * @return the new cursor range to be applied to the text
     */
    @WorkerThread
    @Nullable
    public abstract TextRange formatRegionAsync(@NonNull Content text, @NonNull TextRange rangeToFormat, @NonNull TextRange cursorRange);

    private void sendUpdate(Content text, TextRange cursorRange) {
        FormatResultReceiver r;
        if (!Thread.currentThread().isInterrupted() && receiver != null && (r = receiver.get()) != null) {
            r.onFormatSucceed(text, cursorRange);
        }
    }

    private void sendFailure(Throwable throwable) {
        FormatResultReceiver r;
        if (!Thread.currentThread().isInterrupted() && receiver != null && (r = receiver.get()) != null) {
            r.onFormatFail(throwable);
        }
    }

    @Override
    public void cancel() {
        if (thread != null) {
            final var t = thread;
            if (t.isAlive()) {
                t.interrupt();
            }
            thread = null;
        }
    }

    @Override
    public void destroy() {
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
        thread = null;
        receiver = null;
        text = null;
        range = null;
    }

    private class FormattingThread extends Thread {

        @Override
        public void run() {
            Log.v(LOG_TAG, "AsyncFormatter thread started");
            try {
                while (!isInterrupted()) {
                    lock.lock();
                    if (text == null) {
                        continue;
                    }
                    TextRange newRange;
                    if (range == null) {
                        newRange = formatAsync(text, cursorRange);
                    } else {
                        newRange = formatRegionAsync(text, range, cursorRange);
                    }
                    sendUpdate(text, newRange);
                    // un-refer immediately
                    text = null;
                    range = null;
                    // Wait for next time
                    condition.await();
                }
            } catch (InterruptedException e) {
                Log.v(LOG_TAG, "Thread is interrupted.");
            } catch (Exception e) {
                Log.e(LOG_TAG, "Unexpected exception is thrown in the thread.", e);
                sendFailure(e);
            }
        }
    }
}
