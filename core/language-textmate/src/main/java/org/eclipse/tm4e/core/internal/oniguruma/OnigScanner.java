/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Initial code from https://github.com/atom/node-oniguruma
 * Initial copyright Copyright (c) 2013 GitHub Inc.
 * Initial license: MIT
 *
 * Contributors:
 * - GitHub Inc.: Initial code, written in JavaScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.oniguruma;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href="https://github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc">
 *      github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc</a>
 */
public final class OnigScanner {

	private final OnigSearcher searcher;

	public OnigScanner(final List<String> regexps) {
		searcher = new OnigSearcher(regexps);
	}

	@Nullable
	public OnigScannerMatch findNextMatch(final OnigString source, final int startPosition) {
		final OnigResult bestResult = searcher.search(source, startPosition);
		if (bestResult != null) {
			return new OnigScannerMatch(bestResult, source);
		}
		return null;
	}
}
