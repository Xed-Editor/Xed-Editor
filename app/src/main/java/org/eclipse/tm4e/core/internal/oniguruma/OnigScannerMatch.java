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

import java.util.Arrays;

import org.eclipse.jdt.annotation.Nullable;

/**
 * @see <a href="https://github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc">
 *      github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc</a>
 */
public final class OnigScannerMatch {

	public final int index;
	private final OnigCaptureIndex[] captureIndices;

	OnigScannerMatch(final OnigResult result, final OnigString source) {
		this.index = result.getIndex();
		this.captureIndices = captureIndicesOfMatch(result, source);
	}

	/**
	 * @see <a href="https://github.com/atom/node-oniguruma/blob/0c6b95fc7d79ab7e60a7ed63df6d05677ace2642/src/onig-scanner.cc#L102">
	 *      github.com/atom/node-oniguruma/blob/master/src/onig-scanner.cc#L102</a>
	 */
	private OnigCaptureIndex[] captureIndicesOfMatch(final OnigResult result, final OnigString source) {
		final int resultCount = result.count();
		final var captures = new OnigCaptureIndex[resultCount];
		for (int i = 0; i < resultCount; i++) {
			final int loc = result.locationAt(i);
			final int captureStart = source.getCharIndexOfByte(loc);
			final int captureEnd = source.getCharIndexOfByte(loc + result.lengthAt(i));
			captures[i] = captureStart == 0 && captureEnd == 0
					? OnigCaptureIndex.EMPTY
					: new OnigCaptureIndex(captureStart, captureEnd);
		}
		return captures;
	}

	@Override
	public boolean equals(@Nullable final Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof final OnigScannerMatch other)
			return index == other.index
					&& Arrays.equals(captureIndices, other.captureIndices);
		return false;
	}

	public OnigCaptureIndex[] getCaptureIndices() {
		return captureIndices;
	}

	@Override
	public int hashCode() {
		return 31 * (31 + index) + Arrays.hashCode(captureIndices);
	}

	@Override
	public String toString() {
		final var result = new StringBuilder("{\n");
		result.append("  \"index\": ");
		result.append(index);
		result.append(",\n");
		result.append("  \"captureIndices\": [\n");
		int i = 0;
		for (final OnigCaptureIndex captureIndex : captureIndices) {
			if (i > 0) {
				result.append(",\n");
			}
			result.append("    ");
			result.append(captureIndex);
			i++;
		}
		result.append("\n");
		result.append("  ]\n");
		result.append("}");
		return result.toString();
	}
}
