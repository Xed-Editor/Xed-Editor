/**
 * Copyright (c) 2015-2017 Angelo ZERR.
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
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.rule;

import java.util.List;

import org.eclipse.tm4e.core.internal.oniguruma.OnigScanner;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L858">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
public final class CompiledRule {

	public final List<String> debugRegExps;
	public final OnigScanner scanner;
	public final RuleId[] rules;

	CompiledRule(final List<String> regExps, final RuleId[] rules) {
		this.debugRegExps = regExps;
		this.rules = rules;
		this.scanner = new OnigScanner(regExps);
	}
}
