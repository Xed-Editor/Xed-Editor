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

import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * Describes indentation rules for a language.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/autoIndent.ts#L278">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/autoIndent.ts#L278</a>
 */
public class IndentForEnter {

	public final String beforeEnter;
	public final String afterEnter;

	public IndentForEnter(final String beforeEnter, final String afterEnter) {
		this.beforeEnter = beforeEnter;
		this.afterEnter = afterEnter;
	}

	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> sb
				.append("beforeEnter=").append(beforeEnter).append(", ")
				.append("afterEnter=").append(afterEnter));
	}
}
