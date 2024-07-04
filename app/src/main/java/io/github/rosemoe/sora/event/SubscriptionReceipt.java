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
package io.github.rosemoe.sora.event;

import androidx.annotation.NonNull;

import java.lang.ref.WeakReference;

/**
 * Receipt of {@link EventManager#subscribeEvent(Class, EventReceiver)}. You can unsubscribe the event outside
 * the dispatch process from any thread by calling {@link SubscriptionReceipt#unsubscribe()}
 *
 * @author Rosemoe
 */
public class SubscriptionReceipt<R extends Event> {

    private final Class<R> clazz;
    private final WeakReference<EventReceiver<R>> receiver;
    private final EventManager manager;

    SubscriptionReceipt(@NonNull EventManager mgr, @NonNull Class<R> clazz, @NonNull EventReceiver<R> receiver) {
        this.clazz = clazz;
        this.receiver = new WeakReference<>(receiver);
        this.manager = mgr;
    }

    /**
     * Unsubscribe the event receiver.
     * <p>
     * Does nothing if the listener is already recycled or unsubscribed.
     */
    public void unsubscribe() {
        var receivers = manager.getReceivers(clazz);
        receivers.lock.writeLock().lock();
        try {
            var target = receiver.get();
            if (target != null) {
                receivers.receivers.remove(target);
            }
        } finally {
            receivers.lock.writeLock().unlock();
        }
    }

}
