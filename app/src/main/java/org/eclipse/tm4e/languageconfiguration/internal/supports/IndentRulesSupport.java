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
package org.eclipse.tm4e.languageconfiguration.internal.supports;

import org.eclipse.tm4e.languageconfiguration.internal.model.IndentationRules;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/supports/indentRules.ts">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/supports/indentRules.ts</a>
 */
public class IndentRulesSupport {

	public static final class IndentConsts {
		public static final int INCREASE_MASK = 0b00000001;
		public static final int DECREASE_MASK = 0b00000010;
		public static final int INDENT_NEXTLINE_MASK = 0b00000100;
		public static final int UNINDENT_MASK = 0b00001000;
	}

	private final IndentationRules _indentationRules;

	public IndentRulesSupport(IndentationRules indentationRules) {
		this._indentationRules = indentationRules;
	}

	public boolean shouldIncrease(final String text) {
		return _indentationRules.increaseIndentPattern.matchesFully(text);

		// if (this._indentationRules.indentNextLinePattern && this._indentationRules.indentNextLinePattern.test(text)) {
		// 	return true;
		// }
	}

	public boolean shouldDecrease(final String text) {
		return _indentationRules.decreaseIndentPattern.matchesFully(text);
	}

	public boolean shouldIndentNextLine(final String text) {
		return this._indentationRules.indentNextLinePattern != null && this._indentationRules.indentNextLinePattern.matchesFully(text);
	}

	public boolean shouldIgnore(final String text) {
		// the text matches `unIndentedLinePattern`
		return this._indentationRules.unIndentedLinePattern != null && this._indentationRules.unIndentedLinePattern.matchesFully(text);
	}

	public int getIndentMetadata(final String text) {
		int ret = 0;
		if (this.shouldIncrease(text)) {
			ret += IndentConsts.INCREASE_MASK;
		}
		if (this.shouldDecrease(text)) {
			ret += IndentConsts.DECREASE_MASK;
		}
		if (this.shouldIndentNextLine(text)) {
			ret += IndentConsts.INDENT_NEXTLINE_MASK;
		}
		if (this.shouldIgnore(text)) {
			ret += IndentConsts.UNINDENT_MASK;
		}
		return ret;
	}
}
