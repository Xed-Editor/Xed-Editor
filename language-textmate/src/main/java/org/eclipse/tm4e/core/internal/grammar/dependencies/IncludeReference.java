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
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammarDependencies.ts#L240">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammarDependencies.ts</a>
 */
public final class IncludeReference {
	public enum Kind {
		Base,
		Self,
		RelativeReference,
		TopLevelReference,
		TopLevelRepositoryReference
	}

	public static final IncludeReference BASE = new IncludeReference(Kind.Base, "$base", "");
	public static final IncludeReference SELF = new IncludeReference(Kind.Base, "$self", "");

	public static IncludeReference parseInclude(final String include) {
		return switch (include) {
			case "$base" -> BASE;
			case "$self" -> SELF;
			default -> {
				final var indexOfSharp = include.indexOf("#");
				yield switch (indexOfSharp) {
					case -1 -> new IncludeReference(Kind.TopLevelReference, include, "");
					case 0 -> new IncludeReference(Kind.RelativeReference, "", include.substring(1));
					default -> {
						final var scopeName = include.substring(0, indexOfSharp);
						final var ruleName = include.substring(indexOfSharp + 1);
						yield new IncludeReference(Kind.TopLevelRepositoryReference, scopeName, ruleName);
					}
				};
			}
		};
	}

	public final Kind kind;
	public final String scopeName;
	public final String ruleName;

	private IncludeReference(final Kind kind, final String scopeName, final String ruleName) {
		this.kind = kind;
		this.scopeName = scopeName;
		this.ruleName = ruleName;
	}
}
