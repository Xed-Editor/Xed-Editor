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
package org.eclipse.tm4e.core.internal.theme;

import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/theme.ts#L294">
 *      github.com/microsoft/vscode-textmate/blob/main/src/theme.ts</a>
 */
public class ParsedThemeRule {

	public final String scope;

	@Nullable
	public final List<String> parentScopes;

	public final int index;

	/**
	 * -1 if not set. An or mask of `FontStyle` otherwise.
	 */
	public final int fontStyle;

	@Nullable
	public final String foreground;

	@Nullable
	public final String background;

	public ParsedThemeRule(final String scope, @Nullable final List<String> parentScopes, final int index, final int fontStyle,
			@Nullable final String foreground, @Nullable final String background) {
		this.scope = scope;
		this.parentScopes = parentScopes;
		this.index = index;
		this.fontStyle = fontStyle;
		this.foreground = foreground;
		this.background = background;
	}

	@Override
	public int hashCode() {
		int result = 31 + fontStyle;
		result = 31 * result + index;
		result = 31 * result + Objects.hashCode(background);
		result = 31 * result + Objects.hashCode(foreground);
		result = 31 * result + Objects.hashCode(parentScopes);
		return 31 * result + Objects.hashCode(scope);
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final ParsedThemeRule other)
			return fontStyle == other.fontStyle
					&& index == other.index
					&& Objects.equals(background, other.background)
					&& Objects.equals(foreground, other.foreground)
					&& Objects.equals(parentScopes, other.parentScopes)
					&& Objects.equals(scope, other.scope);
		return false;

	}

	@Override
	public String toString() {
		return "ParsedThemeRule [scope=" + scope + ", parentScopes=" + parentScopes + ", index=" + index
				+ ", fontStyle=" + fontStyle + ", foreground=" + foreground + ", background=" + background + "]";
	}
}
