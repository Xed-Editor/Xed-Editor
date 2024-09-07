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
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.core.internal.grammar;

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.defaultIfNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.grammar.tokenattrs.OptionalStandardTokenType;
import org.eclipse.tm4e.core.internal.utils.RegexSource;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/basicScopesAttributeProvider.ts#L18">
 *      github.com/microsoft/vscode-textmate/blob/main/src/basicScopesAttributeProvider.ts</a>
 */
final class BasicScopeAttributesProvider {

	private final BasicScopeAttributes _defaultAttributes;
	private final ScopeMatcher<Integer /* languageId */> _embeddedLanguagesMatcher;

	private final Map<String /*scopeName*/, BasicScopeAttributes> cache = new HashMap<>();

	BasicScopeAttributesProvider(final int initialLanguage, @Nullable final Map<String, Integer> embeddedLanguages) {
		this._defaultAttributes = new BasicScopeAttributes(initialLanguage, OptionalStandardTokenType.NotSet);
		this._embeddedLanguagesMatcher = new ScopeMatcher<>(defaultIfNull(embeddedLanguages, Collections.emptyMap()));
	}

	BasicScopeAttributes getDefaultAttributes() {
		return this._defaultAttributes;
	}

	BasicScopeAttributes getBasicScopeAttributes(@Nullable final String scopeName) {
		if (scopeName == null) {
			return BasicScopeAttributesProvider._NULL_SCOPE_METADATA;
		}

		return cache.computeIfAbsent(scopeName, scopeName2 -> {
			final var languageId = this._scopeToLanguage(scopeName);
			final var standardTokenType = _toStandardTokenType(scopeName);
			return new BasicScopeAttributes(languageId, standardTokenType);
		});
	}

	private static final BasicScopeAttributes _NULL_SCOPE_METADATA = new BasicScopeAttributes(0, 0);

	/**
	 * Given a produced TM scope, return the language that token describes or null if unknown.
	 * e.g. source.html => html, source.css.embedded.html => css, punctuation.definition.tag.html => null
	 */
	private int _scopeToLanguage(final String scopeName) {
		return defaultIfNull(this._embeddedLanguagesMatcher.match(scopeName), 0);
	}

	private static int /*OptionalStandardTokenType*/ _toStandardTokenType(final String scopeName) {
		final var m = STANDARD_TOKEN_TYPE_REGEXP.matcher(scopeName);
		if (!m.find()) {
			return OptionalStandardTokenType.NotSet;
		}
		final String group = m.group(1);
		return switch (group) {
			case "comment" -> OptionalStandardTokenType.Comment;
			case "string" -> OptionalStandardTokenType.String;
			case "regex" -> OptionalStandardTokenType.RegEx;
			case "meta.embedded" -> OptionalStandardTokenType.Other;
			default -> throw new TMException("Unexpected match for standard token type: " + group);
		};
	}

	private static final Pattern STANDARD_TOKEN_TYPE_REGEXP = Pattern.compile("\\b(comment|string|regex|meta\\.embedded)\\b");

	private static final class ScopeMatcher<TValue> {

		private final Map<String, TValue> values;
		@Nullable
		private final Pattern scopesRegExp;

		ScopeMatcher(final Map<String, TValue> values) {
			if (values.isEmpty()) {
				this.values = Collections.emptyMap();
				this.scopesRegExp = null;
			} else {
				this.values = new HashMap<>(values);

				// create the regex
				final var escapedScopes = values.keySet().stream()
						.map(RegexSource::escapeRegExpCharacters)
						.sorted(Collections.reverseOrder()) // Longest scope first
						.toArray(String[]::new);

				scopesRegExp = Pattern.compile("^((" + String.join(")|(", escapedScopes) + "))($|\\.)");
			}
		}

		@Nullable
		TValue match(final String scopeName) {
			final var scopesRegExp = this.scopesRegExp;
			if (scopesRegExp == null) {
				return null;
			}
			final var m = scopesRegExp.matcher(scopeName);
			if (!m.find()) {
				// no scopes matched
				return null;
			}
			return this.values.get(m.group(1));
		}
	}
}
