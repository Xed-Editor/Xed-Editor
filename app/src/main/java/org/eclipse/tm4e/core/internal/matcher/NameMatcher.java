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
package org.eclipse.tm4e.core.internal.matcher;

import java.util.Collection;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/grammar/grammar.ts#L71">
 *      github.com/microsoft/vscode-textmate/blob/main/src/grammar/grammar.ts</a>
 */
public interface NameMatcher<T> {

	NameMatcher<List<String>> DEFAULT = new NameMatcher<>() {

		@Override
		public boolean matches(final Collection<String> identifiers, final List<String> scopes) {
			if (scopes.size() < identifiers.size()) {
				return false;
			}
			final int[] lastIndex = { 0 };
			return identifiers.stream().allMatch(identifier -> {
				for (int i = lastIndex[0]; i < scopes.size(); i++) {
					if (scopesAreMatching(scopes.get(i), identifier)) {
						lastIndex[0]++;
						return true;
					}
				}
				return false;
			});
		}

		private boolean scopesAreMatching(@Nullable final String thisScopeName, final String scopeName) {
			if (thisScopeName == null) {
				return false;
			}
			if (thisScopeName.equals(scopeName)) {
				return true;
			}
			final int len = scopeName.length();
			return thisScopeName.length() > len
					&& thisScopeName.substring(0, len).equals(scopeName)
					&& thisScopeName.charAt(len) == '.';
		}
	};

	boolean matches(Collection<String> names, T scopes);
}
