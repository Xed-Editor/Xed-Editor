/**
 * Copyright (c) 2024 Sebastian Thomschke and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Sebastian Thomschke - initial API and implementation
 */
package org.eclipse.tm4e.core.internal.utils;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Utility class to deal with plugin scoped TextMate Scope Names, e.g. "source.batchfile@com.example.myplugin"
 */
public final class ScopeNames {

	public static final char CONTRIBUTOR_SEPARATOR = '@';

	public static @Nullable String getContributor(final String scopeName) {
		final int separatorAt = scopeName.indexOf(CONTRIBUTOR_SEPARATOR);
		if (separatorAt == -1) {
			return "";
		}
		return scopeName.substring(separatorAt + 1);
	}

	/**
	 * @return true if a scope name is suffixed by the contributing plugin id, e.g. "source.batchfile@com.example.myplugin"
	 */
	public static boolean hasContributor(final String scopeName) {
		return scopeName.indexOf(CONTRIBUTOR_SEPARATOR) > -1;
	}

	public static String withoutContributor(final String scopeName) {
		final int separatorAt = scopeName.indexOf(CONTRIBUTOR_SEPARATOR);
		if (separatorAt == -1) {
			return scopeName;
		}
		return scopeName.substring(0, separatorAt);
	}

	private ScopeNames() {
	}
}
