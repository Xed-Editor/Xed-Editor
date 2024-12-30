/**
 * Copyright (c) 2023 Vegard IT GmbH and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Sebastian Thomschke - initial implementation
 */
package org.eclipse.tm4e.core.internal.utils;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Thread-safe utility class to manage listener registrations and event dispatching.
 *
 * Sub-classes must implement the method <code>dispatchEvent(LISTENER, EVENT)</code> which is responsible for invoking the appropriate event
 * handler method on the listener.
 *
 * @param <LISTENER> the listener type supported
 * @param <EVENT> the event type dispatched
 */
public abstract class AbstractListeners<LISTENER, EVENT> {

	private final Set<LISTENER> listeners = new CopyOnWriteArraySet<>();

    public boolean add(final LISTENER listener) {
        // LOGGER.log(DEBUG, "Trying to add listener {0} which is already registered with {1}.", listener, this);
        return listeners.add(listener);
    }

	public int count() {
		return listeners.size();
	}

	/**
	 * Forwards the given event to all registered listeners.
	 */
	public void dispatchEvent(final EVENT e) {
		listeners.forEach(l -> dispatchEvent(e, l));
	}

	/**
	 * Forwards the given event to the given listeners.
	 */
	public abstract void dispatchEvent(EVENT e, LISTENER l);

	public boolean isEmpty() {
		return listeners.isEmpty();
	}

	public boolean isNotEmpty() {
		return !listeners.isEmpty();
	}

    public boolean remove(final LISTENER listener) {
        // LOGGER.log(WARNING, "Trying to remove listener {0} which is not registered with {1}.", listener, this);
        return listeners.remove(listener);
    }

	public void removeAll() {
		listeners.clear();
	}
}