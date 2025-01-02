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
package org.eclipse.tm4e.core.internal.grammar.dependencies;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammarDependencies.ts#L10">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammarDependencies.ts</a>
 */
public abstract class AbsoluteRuleReference {

	/**
	 * References the top level rule of a grammar with the given scope name.
	 */
	static final class TopLevelRuleReference extends AbsoluteRuleReference {
		TopLevelRuleReference(final String scopeName) {
			super(scopeName);
		}
	}

	/**
	 * References a rule of a grammar in the top level repository section with the given name.
	 */
	static final class TopLevelRepositoryRuleReference extends AbsoluteRuleReference {
		final String ruleName;

		TopLevelRepositoryRuleReference(final String scopeName, final String ruleName) {
			super(scopeName);
			this.ruleName = ruleName;
		}

		@Override
		String toKey() {
			return this.scopeName + '#' + this.ruleName;
		}
	}

	public final String scopeName;

	private AbsoluteRuleReference(final String scopeName) {
		this.scopeName = scopeName;
	}

	String toKey() {
		return this.scopeName;
	}
}
