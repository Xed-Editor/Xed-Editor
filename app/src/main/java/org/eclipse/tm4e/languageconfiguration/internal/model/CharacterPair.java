/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.languageconfiguration.internal.model;

import androidx.annotation.NonNull;

import org.eclipse.tm4e.core.internal.utils.StringUtils;

import java.util.Objects;

/**
 * A tuple of two characters, like a pair of opening and closing brackets.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/languageConfiguration.ts#L194">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/languageConfiguration.ts#L194</a>
 */
public class CharacterPair {

	public final String open;
	public final String close;

	public CharacterPair(final String opening, final String closing) {
		this.open = opening;
		this.close = closing;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		CharacterPair that = (CharacterPair) o;
		return Objects.equals(open, that.open) && Objects.equals(close, that.close);
	}

	@Override
	public int hashCode() {
		return Objects.hash(open, close);
	}

	@NonNull
	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> sb
				.append("open=").append(open).append(", ")
				.append("close=").append(close));
	}
}
