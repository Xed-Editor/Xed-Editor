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

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href="https://github.com/atom/node-oniguruma/blob/0c6b95fc7d79ab7e60a7ed63df6d05677ace2642/src/onig-scanner.cc#L110">
 *      github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc#L110</a>
 */
public final class OnigCaptureIndex {

	static final OnigCaptureIndex EMPTY = new OnigCaptureIndex(0, 0);

	public final int start;
	public final int end;

	OnigCaptureIndex(final int start, final int end) {
		this.start = start >= 0 ? start : 0;
		this.end = end >= 0 ? end : 0;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final OnigCaptureIndex other)
			return end == other.end
					&& start == other.start;
		return false;
	}

	public int getLength() {
		return end - start;
	}

	@Override
	public int hashCode() {
		return 31 * (31 + end) + start;
	}

	@Override
	public String toString() {
		return "{"
				+ ", \"start\": " + start
				+ ", \"end\": " + end
				+ ", \"length\": " + getLength()
				+ "}";
	}
}
