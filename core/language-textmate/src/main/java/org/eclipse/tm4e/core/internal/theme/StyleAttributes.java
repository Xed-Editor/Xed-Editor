/**
 * Copyright (c) 2022 Sebastian Thomschke.
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
package org.eclipse.tm4e.core.internal.theme;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/theme.ts#L190">
 *      github.com/microsoft/vscode-textmate/blob/main/src/theme.ts</a>
 */
public class StyleAttributes {
	private static final StyleAttributes NO_STYLE = new StyleAttributes(-1, 0, 0);

	public final int fontStyle;
	public final int foregroundId;
	public final int backgroundId;

	public static StyleAttributes of(final int fontStyle, final int foregroundId, final int backgroundId) {
		if (fontStyle == -1 && foregroundId == 0 && backgroundId == 0) {
			return NO_STYLE;
		}
		return new StyleAttributes(fontStyle, foregroundId, backgroundId);
	}

	private StyleAttributes(final int fontStyle, final int foregroundId, final int backgroundId) {
		this.fontStyle = fontStyle;
		this.foregroundId = foregroundId;
		this.backgroundId = backgroundId;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final StyleAttributes other)
			return backgroundId == other.backgroundId
					&& fontStyle == other.fontStyle
					&& foregroundId == other.foregroundId;
		return false;
	}

	@Override
	public int hashCode() {
		int result = 31 + backgroundId;
		result = 31 * result + fontStyle;
		return 31 * result + foregroundId;
	}
}
