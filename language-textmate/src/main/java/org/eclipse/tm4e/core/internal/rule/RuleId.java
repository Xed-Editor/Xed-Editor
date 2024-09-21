/**
 * Copyright (c) 2022 Sebastian Thomschke and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial code from https://github.com/microsoft/vscode-textmate/
 * Initial copyright Copyright (C) Microsoft Corporation. All rights reserved.
 * Initial license: MIT
 *
 * Contributors:
 * - Microsoft Corporation: Initial code, written in TypeScript, licensed under MIT license
 * - Sebastian Thomschke - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.rule;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L14">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
public final class RuleId {

	public static final RuleId NO_RULE = new RuleId(0);

	/**
	 * This is a special constant to indicate that the end regexp matched.
	 */
	public static final RuleId END_RULE = new RuleId(-1);

	/**
	 * This is a special constant to indicate that the while regexp matched.
	 */
	public static final RuleId WHILE_RULE = new RuleId(-2);

	public static RuleId of(final int id) {
		if (id < 0)
			throw new IllegalArgumentException("[id] must be > 0");
		return new RuleId(id);
	}

	public final int id;

	private RuleId(final int id) {
		this.id = id;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final RuleId other)
			return id == other.id;
		return false;
	}

	public boolean equals(final RuleId otherRule) {
		return id == otherRule.id;
	}

	public boolean notEquals(final RuleId otherRule) {
		return id != otherRule.id;
	}

	@Override
	public int hashCode() {
		return id;
	}

	@Override
	public String toString() {
		return Integer.toString(id);
	}
}
