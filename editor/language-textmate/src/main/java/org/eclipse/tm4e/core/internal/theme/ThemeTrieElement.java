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

import static org.eclipse.tm4e.core.internal.utils.MoreCollections.asArrayList;
import static org.eclipse.tm4e.core.internal.utils.StringUtils.strArrCmp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/theme.ts#L481">
 *      github.com/microsoft/vscode-textmate/blob/main/src/theme.ts</a>
 */
public final class ThemeTrieElement {

	private final ThemeTrieElementRule _mainRule;
	private final List<ThemeTrieElementRule> _rulesWithParentScopes;
	private final Map<String /*segment*/, ThemeTrieElement> _children;

	public ThemeTrieElement(final ThemeTrieElementRule mainRule) {
		this(mainRule, new ArrayList<>(), new HashMap<>());
	}

	public ThemeTrieElement(
			final ThemeTrieElementRule mainRule,
			final List<ThemeTrieElementRule> rulesWithParentScopes) {

		this(mainRule, rulesWithParentScopes, new HashMap<>());
	}

	public ThemeTrieElement(
			final ThemeTrieElementRule mainRule,
			final List<ThemeTrieElementRule> rulesWithParentScopes,
			final Map<String /*segment*/, ThemeTrieElement> children) {

		this._mainRule = mainRule;
		this._rulesWithParentScopes = rulesWithParentScopes;
		this._children = children;
	}

	private static List<ThemeTrieElementRule> _sortBySpecificity(final List<ThemeTrieElementRule> arr) {
		if (arr.size() == 1) {
			return arr;
		}
		arr.sort(ThemeTrieElement::_cmpBySpecificity);
		return arr;
	}

	private static int _cmpBySpecificity(final ThemeTrieElementRule a, final ThemeTrieElementRule b) {
		if (a.scopeDepth == b.scopeDepth) {
			final var aParentScopes = a.parentScopes;
			final var bParentScopes = b.parentScopes;
			final int aParentScopesLen = aParentScopes == null ? 0 : aParentScopes.size();
			final int bParentScopesLen = bParentScopes == null ? 0 : bParentScopes.size();
			if (aParentScopesLen == bParentScopesLen) {
				for (int i = 0; i < aParentScopesLen; i++) {
					@SuppressWarnings("null")
					final String aScope = aParentScopes.get(i);
					@SuppressWarnings("null")
					final String bScope = bParentScopes.get(i);
					final int aLen = aScope.length();
					final int bLen = bScope.length();
					if (aLen != bLen) {
						return bLen - aLen;
					}
				}
			}
			return bParentScopesLen - aParentScopesLen;
		}
		return b.scopeDepth - a.scopeDepth;
	}

	public List<ThemeTrieElementRule> match(final String scope) {
		if ("".equals(scope)) {
			return ThemeTrieElement._sortBySpecificity(asArrayList(this._mainRule, this._rulesWithParentScopes));
		}

		final int dotIndex = scope.indexOf('.');
		final String head;
		final String tail;
		if (dotIndex == -1) {
			head = scope;
			tail = "";
		} else {
			head = scope.substring(0, dotIndex);
			tail = scope.substring(dotIndex + 1);
		}

		if (this._children.containsKey(head)) {
			return this._children.get(head).match(tail);
		}

		return ThemeTrieElement._sortBySpecificity(asArrayList(this._mainRule, this._rulesWithParentScopes));
	}

	public void insert(final int scopeDepth, final String scope, @Nullable final List<String> parentScopes, final int fontStyle,
			final int foreground, final int background) {
		if (scope.isEmpty()) {
			this.doInsertHere(scopeDepth, parentScopes, fontStyle, foreground, background);
			return;
		}

		final int dotIndex = scope.indexOf('.');
		final String head;
		final String tail;
		if (dotIndex == -1) {
			head = scope;
			tail = "";
		} else {
			head = scope.substring(0, dotIndex);
			tail = scope.substring(dotIndex + 1);
		}

		final ThemeTrieElement child;
		if (this._children.containsKey(head)) {
			child = this._children.get(head);
		} else {
			child = new ThemeTrieElement(this._mainRule.clone(), ThemeTrieElementRule.cloneArr(this._rulesWithParentScopes));
			this._children.put(head, child);
		}

		child.insert(scopeDepth + 1, tail, parentScopes, fontStyle, foreground, background);
	}

	private void doInsertHere(final int scopeDepth, @Nullable final List<String> parentScopes, int fontStyle, int foreground,
			int background) {

		if (parentScopes == null) {
			// Merge into the main rule
			this._mainRule.acceptOverwrite(scopeDepth, fontStyle, foreground, background);
			return;
		}

		// Try to merge into existing rule
		for (final ThemeTrieElementRule rule : this._rulesWithParentScopes) {
			if (strArrCmp(rule.parentScopes, parentScopes) == 0) {
				// bingo! => we get to merge this into an existing one
				rule.acceptOverwrite(scopeDepth, fontStyle, foreground, background);
				return;
			}
		}

		// Must add a new rule

		// Inherit from main rule
		if (fontStyle == FontStyle.NotSet) {
			fontStyle = this._mainRule.fontStyle;
		}
		if (foreground == 0) {
			foreground = this._mainRule.foreground;
		}
		if (background == 0) {
			background = this._mainRule.background;
		}

		this._rulesWithParentScopes.add(new ThemeTrieElementRule(scopeDepth, parentScopes, fontStyle, foreground, background));
	}

	@Override
	public int hashCode() {
		int result = 31 + _children.hashCode();
		result = 31 * result + _mainRule.hashCode();
		return 31 * result + _rulesWithParentScopes.hashCode();
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final ThemeTrieElement other)
			return _children.equals(other._children)
					&& _mainRule.equals(other._mainRule)
					&& _rulesWithParentScopes.equals(other._rulesWithParentScopes);
		return false;
	}
}
