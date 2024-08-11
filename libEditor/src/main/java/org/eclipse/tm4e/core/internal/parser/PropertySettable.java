/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 * <p>
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Contributors:
 * Sebastian Thomschke - initial implementation
 */
package org.eclipse.tm4e.core.internal.parser;

public interface PropertySettable<V> {

    void setProperty(String name, V value);

    public class ArrayList<T> extends java.util.ArrayList<T> implements PropertySettable<T> {

        private static final long serialVersionUID = 1L;

        @Override
        public void setProperty(final String name, final T value) {
            final var idx = Integer.parseInt(name);
            if (idx == size())
                add(value);
            else
                set(idx, value);
        }
    }

    public class HashMap<T> extends java.util.HashMap<String, T> implements PropertySettable<T> {

        private static final long serialVersionUID = 1L;

        @Override
        public void setProperty(final String name, final T value) {
            put(name, value);
        }
    }
}
