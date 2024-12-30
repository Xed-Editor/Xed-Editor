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
package org.eclipse.tm4e.core.internal.grammar;

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.castNonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes;
import org.eclipse.tm4e.core.internal.theme.FontStyle;
import org.eclipse.tm4e.core.internal.theme.StyleAttributes;
import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammar.ts#L418">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammar.ts</a>
 */
public final class AttributedScopeStack {

	@NonNullByDefault({}) // https://github.com/eclipse-jdt/eclipse.jdt.core/issues/233
	record Frame(int encodedTokenAttributes, List<String> scopeNames) {
	}

	@Nullable
	static AttributedScopeStack fromExtension(final @Nullable AttributedScopeStack namesScopeList,
			final List<AttributedScopeStack.Frame> contentNameScopesList) {
		var current = namesScopeList;
		@Nullable
		ScopeStack scopeNames = namesScopeList != null ? namesScopeList.scopePath : null;
		for (final var frame : contentNameScopesList) {
			scopeNames = ScopeStack.push(scopeNames, frame.scopeNames);
			current = new AttributedScopeStack(current, castNonNull(scopeNames), frame.encodedTokenAttributes);
		}
		return current;
	}

	public static AttributedScopeStack createRoot(final String scopeName,
			final int /*EncodedTokenAttributes*/ tokenAttributes) {
		return new AttributedScopeStack(null, new ScopeStack(null, scopeName), tokenAttributes);
	}

	public static AttributedScopeStack createRootAndLookUpScopeName(final String scopeName, final int encodedTokenAttributes,
			final Grammar grammar) {
		final var rawRootMetadata = grammar.getMetadataForScope(scopeName);
		final var scopePath = new ScopeStack(null, scopeName);
		final var rootStyle = grammar.themeProvider.themeMatch(scopePath);

		final var resolvedTokenAttributes = AttributedScopeStack.mergeAttributes(
				encodedTokenAttributes,
				rawRootMetadata,
				rootStyle);

		return new AttributedScopeStack(null, scopePath, resolvedTokenAttributes);
	}

	public String scopeName() {
		return this.scopePath.scopeName;
	}

	private final @Nullable AttributedScopeStack parent;
	private final ScopeStack scopePath;
	final int tokenAttributes;

	public AttributedScopeStack(
			final @Nullable AttributedScopeStack parent,
			final ScopeStack scopePath,
			final int tokenAttributes) {
		this.parent = parent;
		this.scopePath = scopePath;
		this.tokenAttributes = tokenAttributes;
	}

	@Override
	public String toString() {
		return String.join(" ", this.getScopeNames());
	}

	public boolean equals(final AttributedScopeStack other) {
		return equals(this, other);
	}

	public static boolean equals(
			@Nullable AttributedScopeStack a,
			@Nullable AttributedScopeStack b) {
		do {
			if (a == b) {
				return true;
			}

			if (a == null && b == null) {
				// End of list reached for both
				return true;
			}

			if (a == null || b == null) {
				// End of list reached only for one
				return false;
			}

			if (a.tokenAttributes != b.tokenAttributes
					|| !Objects.equals(a.scopeName(), b.scopeName())) {
				return false;
			}

			// Go to previous pair
			a = a.parent;
			b = b.parent;
		} while (true);
	}

	public static int mergeAttributes(
			final int existingTokenAttributes,
			final BasicScopeAttributes basicScopeAttributes,
			final @Nullable StyleAttributes styleAttributes) {
		var fontStyle = FontStyle.NotSet;
		var foreground = 0;
		var background = 0;

		if (styleAttributes != null) {
			fontStyle = styleAttributes.fontStyle;
			foreground = styleAttributes.foregroundId;
			background = styleAttributes.backgroundId;
		}

		return EncodedTokenAttributes.set(
				existingTokenAttributes,
				basicScopeAttributes.languageId,
				basicScopeAttributes.tokenType,
				null,
				fontStyle,
				foreground,
				background);
	}

	AttributedScopeStack pushAttributed(final @Nullable String scopePath, final Grammar grammar) {
		if (scopePath == null) {
			return this;
		}

		if (scopePath.indexOf(' ') == -1) {
			// This is the common case and much faster
			return _pushAttributed(this, scopePath, grammar);
		}

		final var scopes = StringUtils.splitToArray(scopePath, ' ');
		var result = this;
		for (final var scope : scopes) {
			result = _pushAttributed(result, scope, grammar);
		}
		return result;
	}

	private AttributedScopeStack _pushAttributed(
			final AttributedScopeStack target,
			final String scopeName,
			final Grammar grammar) {
		final var rawMetadata = grammar.getMetadataForScope(scopeName);

		final var newPath = target.scopePath.push(scopeName);
		final var scopeThemeMatchResult = grammar.themeProvider.themeMatch(newPath);
		final var metadata = mergeAttributes(
				target.tokenAttributes,
				rawMetadata,
				scopeThemeMatchResult);
		return new AttributedScopeStack(target, newPath, metadata);
	}

	List<String> getScopeNames() {
		return this.scopePath.getSegments();
	}

	public List<AttributedScopeStack.Frame> getExtensionIfDefined(final @Nullable AttributedScopeStack base) {
		final var result = new ArrayList<AttributedScopeStack.Frame>();
		var self = this;

		while (self != null && self != base) {
			final var parent = self.parent;
			result.add(new AttributedScopeStack.Frame(
					self.tokenAttributes,
					self.scopePath.getExtensionIfDefined(parent != null ? parent.scopePath : null)));
			self = self.parent;
		}
		if (self == base) {
			Collections.reverse(result);
			return result;
		}
		return Collections.emptyList();
	}
}
