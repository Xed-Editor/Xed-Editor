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

import org.joni.Region;

/**
 * @see <a href="https://github.com/atom/node-oniguruma/blob/master/src/onig-result.cc">
 *      github.com/atom/node-oniguruma/blob/master/src/onig-result.cc</a>
 */
public final class OnigResult {

	private int indexInScanner;
	private final Region region;

	OnigResult(final Region region, final int indexInScanner) {
		this.region = region;
		this.indexInScanner = indexInScanner;
	}

	public int getIndex() {
		return indexInScanner;
	}

	void setIndex(final int index) {
		indexInScanner = index;
	}

	public int locationAt(final int index) {
		final int bytes = region.getBeg(index);
		return bytes > 0 ? bytes : 0;
	}

	public int count() {
		return region.getNumRegs();
	}

	public int lengthAt(final int index) {
		final int bytes = region.getEnd(index) - region.getBeg(index);
		return bytes > 0 ? bytes : 0;
	}

	@Override
	public String toString() {
		return "OnigResult [indexInScanner=" + indexInScanner + ", region=" + region + "]";
	}
}
