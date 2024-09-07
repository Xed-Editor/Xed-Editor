/**
 * Copyright (c) 2015-2022 Angelo ZERR.
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
package org.eclipse.tm4e.core.internal.grammar;

import static org.eclipse.tm4e.core.internal.utils.MoreCollections.getElementAt;
import static org.eclipse.tm4e.core.internal.utils.MoreCollections.getLastElement;
import static org.eclipse.tm4e.core.internal.utils.MoreCollections.removeLastElement;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.grammar.IToken;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.EncodedTokenAttributes;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.OptionalStandardTokenType;
import org.eclipse.tm4e.core.internal.theme.FontStyle;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammar.ts#L945">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammar.ts</a>
 */
final class LineTokens {

	private static final class Token implements IToken {
		private int startIndex;
		private final int endIndex;
		private final List<String> scopes;

		Token(final int startIndex, final int endIndex, final List<String> scopes) {
			this.startIndex = startIndex;
			this.endIndex = endIndex;
			this.scopes = scopes;
		}

		@Override
		public int getStartIndex() {
			return startIndex;
		}

		@Override
		public void setStartIndex(final int startIndex) {
			this.startIndex = startIndex;
		}

		@Override
		public int getEndIndex() {
			return endIndex;
		}

		@Override
		public List<String> getScopes() {
			return scopes;
		}

		@Override
		public String toString() {
			return "{"
					+ "startIndex: " + startIndex
					+ ", endIndex: " + endIndex
					+ ", scopes: " + scopes
					+ "}";
		}
	}

    private static final Deque<IToken> EMPTY_DEQUE = new ArrayDeque<>(0);

	private final boolean _emitBinaryTokens;

	/**
	 * defined only if `LOGGER.isLoggable(TRACE)`.
	 */
	private final String _lineText;

	/**
	 * used only if `emitBinaryTokens` is false.
	 */
	private final Deque<IToken> _tokens;

	/**
	 * used only if `emitBinaryTokens` is true.
	 */
	private final List<Integer> _binaryTokens;

	private int _lastTokenEndIndex = 0;

	private final List<TokenTypeMatcher> _tokenTypeOverrides;

	@Nullable
	private final BalancedBracketSelectors balancedBracketSelectors;

	LineTokens(final boolean emitBinaryTokens,
			final String lineText,
			final List<TokenTypeMatcher> tokenTypeOverrides,
			@Nullable final BalancedBracketSelectors balancedBracketSelectors) {

		this._emitBinaryTokens = emitBinaryTokens;
		this._tokenTypeOverrides = tokenTypeOverrides;
		this._lineText = /*LOGGER.isLoggable(TRACE) ? lineText :*/ ""; // store line only if it's logged
		if (this._emitBinaryTokens) {
			this._tokens = EMPTY_DEQUE;
			this._binaryTokens = new ArrayList<>();
		} else {
			this._tokens = new ArrayDeque<>();
			this._binaryTokens = Collections.emptyList();
		}
		this.balancedBracketSelectors = balancedBracketSelectors;
	}

	void produce(final StateStack stack, final int endIndex) {
		this.produceFromScopes(stack.contentNameScopesList, endIndex);
	}

	void produceFromScopes(@Nullable final AttributedScopeStack scopesList, final int endIndex) {
		if (this._lastTokenEndIndex >= endIndex) {
			return;
		}

		if (this._emitBinaryTokens) {
			int metadata = scopesList != null ? scopesList.tokenAttributes : 0;
			var containsBalancedBrackets = false;
			final var balancedBracketSelectors = this.balancedBracketSelectors;
			if (balancedBracketSelectors != null && balancedBracketSelectors.matchesAlways()) {
				containsBalancedBrackets = true;
			}

			if (!_tokenTypeOverrides.isEmpty()
					|| balancedBracketSelectors != null
							&& !balancedBracketSelectors.matchesAlways() && !balancedBracketSelectors.matchesNever()) {
				// Only generate scope array when required to improve performance
				final List<String> scopes = scopesList != null ? scopesList.getScopeNames() : Collections.emptyList();
				for (final var tokenType : _tokenTypeOverrides) {
					if (tokenType.matcher.matches(scopes)) {
						metadata = EncodedTokenAttributes.set(
								metadata,
								0,
								tokenType.type, // toOptionalTokenType(tokenType.type),
								null,
								FontStyle.NotSet,
								0,
								0);
					}
				}
				if (balancedBracketSelectors != null) {
					containsBalancedBrackets = balancedBracketSelectors.match(scopes);
				}
			}

			if (containsBalancedBrackets) {
				metadata = EncodedTokenAttributes.set(
						metadata,
						0,
						OptionalStandardTokenType.NotSet,
						containsBalancedBrackets,
						FontStyle.NotSet,
						0,
						0);
			}

			if (!this._binaryTokens.isEmpty() && getLastElement(this._binaryTokens) == metadata) {
				// no need to push a token with the same metadata
				this._lastTokenEndIndex = endIndex;
				return;
			}

            /* if (LOGGER.isLoggable(TRACE)) {
                final List<String> scopes = scopesList != null ? scopesList.getScopeNames() : Collections.emptyList();
                LOGGER.log(TRACE, "  token: |" + this._lineText
                        .substring(this._lastTokenEndIndex >= 0 ? this._lastTokenEndIndex : 0, endIndex)
                        .replace("\n", "\\n")
                        + '|');
                for (final String scope : scopes) {
                    LOGGER.log(TRACE, "      * " + scope);
                }
            }*/

			this._binaryTokens.add(this._lastTokenEndIndex);
			this._binaryTokens.add(metadata);

			this._lastTokenEndIndex = endIndex;
			return;
		}

		final List<String> scopes = scopesList != null ? scopesList.getScopeNames() : Collections.emptyList();

        /*if (LOGGER.isLoggable(TRACE)) {
            LOGGER.log(TRACE, "  token: |" + this._lineText
                    .substring(this._lastTokenEndIndex >= 0 ? this._lastTokenEndIndex : 0, endIndex)
                    .replace("\n", "\\n")
                    + '|');
            for (final String scope : scopes) {
                LOGGER.log(TRACE, "      * " + scope);
            }
        }*/

		this._tokens.add(new Token(_lastTokenEndIndex, endIndex, scopes));

		this._lastTokenEndIndex = endIndex;
	}

	IToken[] getResult(final StateStack stack, final int lineLength) {
		if (!this._tokens.isEmpty() && this._tokens.getLast().getStartIndex() == lineLength - 1) {
			// pop produced token for newline
			this._tokens.removeLast();
		}

		if (this._tokens.isEmpty()) {
			this._lastTokenEndIndex = -1;
			this.produce(stack, lineLength);
			this._tokens.getLast().setStartIndex(0);
		}

		return this._tokens.toArray(new IToken[0]);
	}

	int[] getBinaryResult(final StateStack stack, final int lineLength) {
		if (!this._binaryTokens.isEmpty() && getElementAt(this._binaryTokens, -2) == lineLength - 1) {
			// pop produced token for newline
			removeLastElement(this._binaryTokens);
			removeLastElement(this._binaryTokens);
		}

		if (this._binaryTokens.isEmpty()) {
			this._lastTokenEndIndex = -1;
			this.produce(stack, lineLength);
			this._binaryTokens.set(_binaryTokens.size() - 2, 0);
		}

		return _binaryTokens.stream().mapToInt(Integer::intValue).toArray();
	}
}
