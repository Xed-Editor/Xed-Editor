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

import java.util.List;

@FunctionalInterface
public interface Matcher<T> {

	static List<MatcherWithPriority<List<String>>> createMatchers(final String selector) {
		return createMatchers(selector, NameMatcher.DEFAULT);
	}

	static List<MatcherWithPriority<List<String>>> createMatchers(final String selector,
			final NameMatcher<List<String>> matchesName) {
		return new MatcherBuilder<>(selector, matchesName).results;
	}

	boolean matches(T t);
}
