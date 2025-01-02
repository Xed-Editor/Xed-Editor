/**
 * Copyright (c) 2024 Vegard IT GmbH and others.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * - Sebastian Thomschke - initial implementation
 */
package org.eclipse.tm4e.languageconfiguration.internal.model;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp;
import org.eclipse.tm4e.core.internal.oniguruma.OnigString;

public abstract class RegExPattern {

	private static final class JavaRegExPattern extends RegExPattern {
		final Pattern pattern;

		JavaRegExPattern(final String pattern, final @Nullable String flags) throws PatternSyntaxException {
			// maybe unsupported in android?
			this.pattern = Pattern.compile(flags == null ? pattern : pattern + "(?" + flags + ")");
		}

		@Override
		public boolean matchesFully(final String text) {
			return pattern.matcher(text).matches();
		}

		@Override
		public boolean matchesPartially(final String text) {
			return pattern.matcher(text).find();
		}

		@Override
		public String pattern() {
			return pattern.pattern();
		}
	}

	private static final class OnigRegExPattern extends RegExPattern {
		final OnigRegExp regex;

		OnigRegExPattern(final String pattern, final @Nullable String flags) throws PatternSyntaxException {
			this.regex = new OnigRegExp(pattern, flags != null && flags.contains("i"));
		}

		@Override
		public boolean matchesFully(final String text) {
			final var result = regex.search(OnigString.of(text), 0);
			return result != null && result.count() == 1 && result.lengthAt(0) == text.length();
		}

		@Override
		public boolean matchesPartially(final String text) {
			return regex.search(OnigString.of(text), 0) != null;
		}

		@Override
		public String pattern() {
			return regex.pattern();
		}
	}

	/**
	 * @param pattern {@link Pattern} or {@link OnigRegExp} compatible pattern
	 *
	 * @throws TMException if pattern parsing fails
	 */
	public static RegExPattern of(final String pattern) {
		return of(pattern, null);
	}

	/**
	 * @param pattern {@link Pattern} or {@link OnigRegExp} compatible pattern
	 *
	 * @throws TMException if pattern parsing fails
	 */
	public static RegExPattern of(final String pattern, final @Nullable String flags) {
		try {
			return new JavaRegExPattern(pattern, flags);
		} catch (Exception ex) {
			// try onigurama as fallback
			return new OnigRegExPattern(pattern, flags);
		}
	}

	/**
	 * @param pattern {@link Pattern} or {@link OnigRegExp} compatible pattern
	 *
	 * @return null if pattern is null or the pattern is invalid
	 */
	public static @Nullable RegExPattern ofNullable(final @Nullable String pattern) {
		return ofNullable(pattern, null);
	}

	/**
	 * @param pattern {@link Pattern} or {@link OnigRegExp} compatible pattern
	 *
	 * @return null if pattern is null or the pattern is invalid
	 */
	public static @Nullable RegExPattern ofNullable(final @Nullable String pattern, final @Nullable String flags) {
		if (pattern != null) {
			try {
				return new JavaRegExPattern(pattern, flags);
			} catch (Exception ex) {
				try {
					// try onigurama as fallback
					return new OnigRegExPattern(pattern, flags);
				} catch (Exception ex1) {
					ex1.printStackTrace();
				}
			}
		}
		return null;
	}

	public abstract boolean matchesFully(String text);

	public abstract boolean matchesPartially(String text);

	public abstract String pattern();

	@Override
	public String toString() {
		return pattern();
	}
}
