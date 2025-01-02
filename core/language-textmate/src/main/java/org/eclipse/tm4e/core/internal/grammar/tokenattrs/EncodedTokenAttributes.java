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
package org.eclipse.tm4e.core.internal.grammar.tokenattrs;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.theme.FontStyle;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/encodedTokenAttributes.ts#L9">
 *      github.com/microsoft/vscode-textmate/blob/main/src/encodedTokenAttributes.ts</a>
 */
public final class EncodedTokenAttributes {

	public static String toBinaryStr(final int encodedTokenAttributes) {
		return new StringBuilder(Integer.toBinaryString(encodedTokenAttributes))
				.insert(0, "0".repeat(Integer.numberOfLeadingZeros(encodedTokenAttributes)))
				.toString();
	}

	public static String toString(final int encodedTokenAttributes) {
		final var languageId = getLanguageId(encodedTokenAttributes);
		final var tokenType = getTokenType(encodedTokenAttributes);
		final var fontStyle = getFontStyle(encodedTokenAttributes);
		final var foreground = getForeground(encodedTokenAttributes);
		final var background = getBackground(encodedTokenAttributes);
		final var containsBalancedBrackets = containsBalancedBrackets(encodedTokenAttributes);

		return "{\n"
				+ "  languageId: " + languageId + ",\n"
				+ "  tokenType: " + tokenType + ",\n"
				+ "  fontStyle: " + fontStyle + ",\n"
				+ "  foreground: " + foreground + ",\n"
				+ "  background: " + background + "\n,"
				+ "  containsBalancedBrackets: " + containsBalancedBrackets + "\n"
				+ "}";
	}

	public static int getLanguageId(final int metadata) {
		return (metadata & EncodedTokenDataConsts.LANGUAGEID_MASK) >>> EncodedTokenDataConsts.LANGUAGEID_OFFSET;
	}

	public static int getTokenType(final int metadata) {
		return (metadata & EncodedTokenDataConsts.TOKEN_TYPE_MASK) >>> EncodedTokenDataConsts.TOKEN_TYPE_OFFSET;
	}

	public static boolean containsBalancedBrackets(final int metadata) {
		return (metadata & EncodedTokenDataConsts.BALANCED_BRACKETS_MASK) != 0;
	}

	public static int getFontStyle(final int metadata) {
		return (metadata & EncodedTokenDataConsts.FONT_STYLE_MASK) >>> EncodedTokenDataConsts.FONT_STYLE_OFFSET;
	}

	public static int getForeground(final int metadata) {
		return (metadata & EncodedTokenDataConsts.FOREGROUND_MASK) >>> EncodedTokenDataConsts.FOREGROUND_OFFSET;
	}

	public static int getBackground(final int metadata) {
		return (metadata & EncodedTokenDataConsts.BACKGROUND_MASK) >>> EncodedTokenDataConsts.BACKGROUND_OFFSET;
	}

	/**
	 * Updates the fields in `metadata`.
	 * A value of `0`, `NotSet` or `null` indicates that the corresponding field should be left as is.
	 */
	public static int set(final int metadata, final int languageId, final /*OptionalStandardTokenType*/ int tokenType,
			@Nullable final Boolean containsBalancedBrackets, final int fontStyle, final int foreground, final int background) {
		final var _languageId = languageId == 0 ? getLanguageId(metadata) : languageId;
		final var _tokenType = tokenType == OptionalStandardTokenType.NotSet ? getTokenType(metadata) : tokenType;
		final var _containsBalancedBracketsBit = (containsBalancedBrackets == null
				? containsBalancedBrackets(metadata)
				: containsBalancedBrackets) ? 1 : 0;
		final var _fontStyle = fontStyle == FontStyle.NotSet ? getFontStyle(metadata) : fontStyle;
		final var _foreground = foreground == 0 ? getForeground(metadata) : foreground;
		final var _background = background == 0 ? getBackground(metadata) : background;

		return (_languageId << EncodedTokenDataConsts.LANGUAGEID_OFFSET
				| _tokenType << EncodedTokenDataConsts.TOKEN_TYPE_OFFSET
				| _containsBalancedBracketsBit << EncodedTokenDataConsts.BALANCED_BRACKETS_OFFSET
				| _fontStyle << EncodedTokenDataConsts.FONT_STYLE_OFFSET
				| _foreground << EncodedTokenDataConsts.FOREGROUND_OFFSET
				| _background << EncodedTokenDataConsts.BACKGROUND_OFFSET) >>> 0;
	}

	/**
	 * Content should be referenced statically
	 */
	private EncodedTokenAttributes() {
	}
}
