/**
 * Copyright (c) 2015-2019 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.core.internal.grammar.raw;

import java.util.NoSuchElementException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.parser.PropertySettable;

public final class RawRepository extends PropertySettable.HashMap<IRawRule> implements IRawRepository {

	private static final long serialVersionUID = 1L;

	public static final String DOLLAR_BASE = "$base";
	public static final String DOLLAR_SELF = "$self";

	@SuppressWarnings({ "null", "unused" })
	private IRawRule getSafe(final String key) {
		final IRawRule obj = get(key);
		if (obj == null) {
			throw new NoSuchElementException("Key '" + key + "' does not exit found");
		}
		return obj;
	}

	@Override
	@Nullable
	public IRawRule getRule(final String name) {
		return get(name);
	}

	@Override
	public IRawRule getBase() {
		return getSafe(DOLLAR_BASE);
	}

	@Override
	public void setBase(final IRawRule base) {
		super.put(DOLLAR_BASE, base);
	}

	@Override
	public IRawRule getSelf() {
		return getSafe(DOLLAR_SELF);
	}

	@Override
	public void setSelf(final IRawRule self) {
		super.put(DOLLAR_SELF, self);
	}

	@Override
	public void putEntries(final PropertySettable<IRawRule> target) {
		for (final var entry : entrySet()) {
			target.setProperty(entry.getKey(), entry.getValue());
		}
	}
}
