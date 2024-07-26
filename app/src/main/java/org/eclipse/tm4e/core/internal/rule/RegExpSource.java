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
package org.eclipse.tm4e.core.internal.rule;

import java.util.ArrayList;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.oniguruma.OnigCaptureIndex;
import org.eclipse.tm4e.core.internal.utils.RegexSource;

import io.github.rosemoe.sora.langs.textmate.utils.MatcherUtils;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L582">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
final class RegExpSource {

	private static final Pattern HAS_BACK_REFERENCES = Pattern.compile("\\\\(\\d+)");
	private static final Pattern BACK_REFERENCING_END = Pattern.compile("\\\\(\\d+)");

	private String source;
	final RuleId ruleId;
	final boolean hasBackReferences;

	private String @Nullable [][] anchorCache;

	RegExpSource(final String regExpSource, final RuleId ruleId) {
		this(regExpSource, ruleId, true);
	}

	RegExpSource(final String regExpSource, final RuleId ruleId, final boolean handleAnchors) {
		if (handleAnchors && !regExpSource.isEmpty()) {
			final int len = regExpSource.length();
			int lastPushedPos = 0;
			final var output = new StringBuilder();

			boolean hasAnchors = false;
			for (int pos = 0; pos < len; pos++) {
				final char ch = regExpSource.charAt(pos);

				if (ch == '\\') {
					if (pos + 1 < len) {
						final char nextCh = regExpSource.charAt(pos + 1);
						if (nextCh == 'z') {
							output.append(regExpSource.substring(lastPushedPos, pos));
							output.append("$(?!\\n)(?<!\\n)");
							lastPushedPos = pos + 2;
						} else if (nextCh == 'A' || nextCh == 'G') {
							hasAnchors = true;
						}
						pos++;
					}
				}
			}

			if (lastPushedPos == 0) {
				// No \z hit
				source = regExpSource;
			} else {
				output.append(regExpSource.substring(lastPushedPos, len));
				source = output.toString();
			}
			if (hasAnchors) {
				anchorCache = buildAnchorCache();
			}
		} else {
			this.source = regExpSource;
		}

		this.ruleId = ruleId;
		this.hasBackReferences = HAS_BACK_REFERENCES.matcher(this.source).find();
	}

	@Override
	protected RegExpSource clone() {
		return new RegExpSource(source, this.ruleId);
	}

	void setSource(final String newSource) {
		if (Objects.equals(source, newSource)) {
			return;
		}
		this.source = newSource;

		if (hasAnchor()) {
			this.anchorCache = buildAnchorCache();
		}
	}

	@SuppressWarnings("null")
	String resolveBackReferences(final CharSequence lineText, final OnigCaptureIndex[] captureIndices) {
		final var capturedValues = new ArrayList<String>(captureIndices.length);
		for (final var capture : captureIndices) {
			capturedValues.add(lineText.subSequence(capture.start, capture.end).toString());
		}
		return MatcherUtils.replaceAll(this.source, BACK_REFERENCING_END.matcher(this.source), (match -> {
			try {
				final int index = Integer.parseInt(match.group(1));
				if (index < captureIndices.length) {
					final var replacement = RegexSource.escapeRegExpCharacters(capturedValues.get(index));
					return Matcher.quoteReplacement(replacement); // see https://stackoverflow.com/a/70785772/5116073
				}
			} catch (final NumberFormatException ex) {
				// ignore
			}
			return "";
		}));
	}

	private String[][] buildAnchorCache() {
		final var source = this.source;
		final var sourceLen = source.length();

		final var A0_G0_result = new StringBuilder(sourceLen);
		final var A0_G1_result = new StringBuilder(sourceLen);
		final var A1_G0_result = new StringBuilder(sourceLen);
		final var A1_G1_result = new StringBuilder(sourceLen);

		for (int pos = 0, len = sourceLen; pos < len; pos++) {
			final char ch = source.charAt(pos);
			A0_G0_result.append(ch);
			A0_G1_result.append(ch);
			A1_G0_result.append(ch);
			A1_G1_result.append(ch);

			if (ch == '\\' && pos + 1 < len) {
				final char nextCh = source.charAt(pos + 1);
				if (nextCh == 'A') {
					A0_G0_result.append('\uFFFF');
					A0_G1_result.append('\uFFFF');
					A1_G0_result.append('A');
					A1_G1_result.append('A');
				} else if (nextCh == 'G') {
					A0_G0_result.append('\uFFFF');
					A0_G1_result.append('G');
					A1_G0_result.append('\uFFFF');
					A1_G1_result.append('G');
				} else {
					A0_G0_result.append(nextCh);
					A0_G1_result.append(nextCh);
					A1_G0_result.append(nextCh);
					A1_G1_result.append(nextCh);
				}
				pos++;
			}
		}

		return new String[][] {
			{ A0_G0_result.toString(), A0_G1_result.toString() },
			{ A1_G0_result.toString(), A1_G1_result.toString() }
		};
	}

	String resolveAnchors(final boolean allowA, final boolean allowG) {
		final var anchorCache = this.anchorCache;
		if (anchorCache == null) {
			return this.source;
		}

		return anchorCache[allowA ? 1 : 0][allowG ? 1 : 0];
	}

	boolean hasAnchor() {
		return anchorCache != null;
	}

	String getSource() {
		return this.source;
	}
}
