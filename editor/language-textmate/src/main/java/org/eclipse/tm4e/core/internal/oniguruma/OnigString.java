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
 * - Fabio Zadrozny <fabiofz@gmail.com> - Convert uniqueId to Object (for identity compare)
 * - Fabio Zadrozny <fabiofz@gmail.com> - Utilities to convert between utf-8 and utf-16
 */
package org.eclipse.tm4e.core.internal.oniguruma;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.eclipse.jdt.annotation.Nullable;
import org.jcodings.specific.UTF8Encoding;

/**
 * Oniguruma string.
 *
 * @see <a href="https://github.com/atom/node-oniguruma/blob/main/src/onig-string.cc">
 *      github.com/atom/node-oniguruma/blob/main/src/onig-string.cc</a>
 *
 */
public abstract class OnigString {

	/**
	 * Represents a string that contains multi-byte characters
	 */
	static final class MultiByteString extends OnigString {

		/**
		 * For each byte holds the index of the char to which the byte belongs.
		 * E.g. in case of <code>byteToCharOffsets[100] == 60 && byteToCharOffsets[101] == 60</code>,
		 * the bytes at indexes 100 and 101 both belong to the same multi-byte character at index 60.
		 */
		private int @Nullable [] byteToCharOffsets;
		private final int lastCharIndex;

		private MultiByteString(final String str, final byte[] bytesUTF8) {
			super(str, bytesUTF8);
			lastCharIndex = str.length() - 1;
		}

		@Override
		int getByteIndexOfChar(final int charIndex) {
			if (charIndex == lastCharIndex + 1) {
				// One off can happen when finding the end of a regexp (it's the right boundary).
				return bytesCount;
			}

			if (charIndex < 0 || charIndex > lastCharIndex) {
				throwOutOfBoundsException("Char", charIndex, 0, lastCharIndex);
			}
			if (charIndex == 0) {
				return 0;
			}

			final int[] byteToCharOffsets = getByteToCharOffsets();
			int byteIndex = Arrays.binarySearch(byteToCharOffsets, charIndex);
			while (byteIndex > 0 && byteToCharOffsets[byteIndex - 1] == charIndex) {
				byteIndex--;
			}
			return byteIndex;
		}

		private int[] getByteToCharOffsets() {
			int[] offsets = byteToCharOffsets;
			if (offsets == null) {
				offsets = new int[bytesCount];
				int charIndex = 0;
				int byteIndex = 0;
				final int maxByteIndex = bytesCount - 1;
				while (byteIndex <= maxByteIndex) {
					final int charLenInBytes = UTF8Encoding.INSTANCE.length(bytesUTF8, byteIndex, bytesCount);
					// same as "Arrays.fill(offsets, byteIndex, byteIndex + charLenInBytes, charIndex)" but faster
					for (final int l = byteIndex + charLenInBytes; byteIndex < l; byteIndex++) {
						offsets[byteIndex] = charIndex;
					}
					charIndex++;
				}
				byteToCharOffsets = offsets;
			}
			return offsets;
		}

		@Override
		int getCharIndexOfByte(final int byteIndex) {
			if (byteIndex == bytesCount) {
				// One off can happen when finding the end of a regexp (it's the right boundary).
				return lastCharIndex + 1;
			}

			if (byteIndex < 0 || byteIndex >= bytesCount) {
				throwOutOfBoundsException("Byte", byteIndex, 0, bytesCount - 1);
			}
			if (byteIndex == 0) {
				return 0;
			}

			return getByteToCharOffsets()[byteIndex];
		}
	}

	/**
	 * Represents a string is only composed of single-byte characters
	 */
	static final class SingleByteString extends OnigString {

		private SingleByteString(final String str, final byte[] bytesUTF8) {
			super(str, bytesUTF8);
		}

		@Override
		int getByteIndexOfChar(final int charIndex) {
			if (charIndex == bytesCount) {
				// One off can happen when finding the end of a regexp (it's the right boundary).
				return charIndex;
			}

			if (charIndex < 0 || charIndex >= bytesCount) {
				throwOutOfBoundsException("Char", charIndex, 0, bytesCount - 1);
			}
			return charIndex;
		}

		@Override
		int getCharIndexOfByte(final int byteIndex) {
			if (byteIndex == bytesCount) {
				// One off can happen when finding the end of a regexp (it's the right boundary).
				return byteIndex;
			}

			if (byteIndex < 0 || byteIndex >= bytesCount) {
				throwOutOfBoundsException("Byte", byteIndex, 0, bytesCount - 1);
			}
			return byteIndex;
		}
	}

	public static OnigString of(final String str) {
		final byte[] bytesUtf8 = str.getBytes(StandardCharsets.UTF_8);
		if (bytesUtf8.length == str.length()) {
			return new SingleByteString(str, bytesUtf8);
		}
		return new MultiByteString(str, bytesUtf8);
	}

	public final String content;

	public final int bytesCount;
	final byte[] bytesUTF8;

	private OnigString(final String content, final byte[] bytesUTF8) {
		this.content = content;
		this.bytesUTF8 = bytesUTF8;
		bytesCount = bytesUTF8.length;
	}

	protected final void throwOutOfBoundsException(final String indexName, final int index, final int minIndex, final int maxIndex) {
		throw new ArrayIndexOutOfBoundsException(
				indexName + " index " + index + " is out of range " + minIndex + ".." + maxIndex + " of " + this);
	}

	abstract int getByteIndexOfChar(int charIndex);

	abstract int getCharIndexOfByte(int byteIndex);

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[string=\"" + content + "\"]";
	}
}
