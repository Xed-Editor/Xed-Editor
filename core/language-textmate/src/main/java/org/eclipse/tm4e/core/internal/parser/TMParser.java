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
 * Sebastian Thomschke (Vegard IT) - initial implementation
 */
package org.eclipse.tm4e.core.internal.parser;

import java.io.Reader;
import java.util.List;
import java.util.Map;

public interface TMParser {

	interface ObjectFactory<T extends PropertySettable<?>> {

		T createRoot();

		/**
		 * @param sourceType {@link Map} | {@link List}
		 */
		PropertySettable<?> createChild(PropertyPath path, final Class<?> sourceType);
	}

	/**
	 * Represents the hierarchical path of a property, e.g.
	 * <li><code>/fileTypes</code>
	 * <li><code>/fileTypes/0</code>
	 * <li><code>/scopeName</code>
	 * <li><code>/patterns/0/captures/0/name</code>
	 * <li><code>/repository/constants/patterns/0/name</code>
	 * <li><code>/repository/statements/patterns/3/include</code>
	 * <li><code>/repository/variable/patterns/1/captures/1/name</code>
	 */
	interface PropertyPath extends Iterable<Object> {

		/**
		 * @return {@link String} | {@link Integer}
		 *
		 * @throw NoSuchElementException
		 */
		Object first();

		/**
		 * @param index 0-based
		 *
		 * @return {@link String} | {@link Integer}
		 *
		 * @throws IndexOutOfBoundsException
		 */
		Object get(int index);

		/**
		 * @return {@link String} | {@link Integer}
		 *
		 * @throw NoSuchElementException
		 */
		Object last();

		int depth();
	}

	<T extends PropertySettable<?>> T parse(Reader source, ObjectFactory<T> factory) throws Exception;
}
