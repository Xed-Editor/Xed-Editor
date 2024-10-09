/**
 * Copyright (c) 2024 Vegard IT GmbH and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial code from https://github.com/microsoft/vscode/
 * Initial copyright Copyright (C) Microsoft Corporation. All rights reserved.
 * Initial license: MIT
 *
 * Contributors:
 * - Microsoft Corporation: Initial code, written in TypeScript, licensed under MIT license
 * - Sebastian Thomschke - translation and adaptation to Java
 */
package org.eclipse.tm4e.languageconfiguration.internal.model;

import org.eclipse.jdt.annotation.Nullable;

/**
 * Describes indentation rules for a language.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/languageConfiguration.ts#L105">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/languageConfiguration.ts#L105</a>
 */
public class IndentationRules {

	/**
	 * If a line matches this pattern, then all the lines after it should be unindented once (until another rule matches).
	 */
	public final RegExPattern decreaseIndentPattern;

	/**
	 * If a line matches this pattern, then all the lines after it should be indented once (until another rule matches).
	 */
	public final RegExPattern increaseIndentPattern;

	/**
	 * If a line matches this pattern, then **only the next line** after it should be indented once.
	 */
	public final @Nullable RegExPattern indentNextLinePattern;

	/**
	 * If a line matches this pattern, then its indentation should not be changed and it should not be evaluated against the other rules.
	 */
	public final @Nullable RegExPattern unIndentedLinePattern;

	public IndentationRules(final RegExPattern decreaseIndentPattern, final RegExPattern increaseIndentPattern,
			final @Nullable RegExPattern indentNextLinePattern, final @Nullable RegExPattern unIndentedLinePattern) {
		this.decreaseIndentPattern = decreaseIndentPattern;
		this.increaseIndentPattern = increaseIndentPattern;
		this.indentNextLinePattern = indentNextLinePattern;
		this.unIndentedLinePattern = unIndentedLinePattern;
	}
}