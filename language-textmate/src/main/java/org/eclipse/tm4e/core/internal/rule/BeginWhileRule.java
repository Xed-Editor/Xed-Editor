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

import static org.eclipse.tm4e.core.internal.utils.NullSafetyHelper.defaultIfNull;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.oniguruma.OnigCaptureIndex;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L290">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
public final class BeginWhileRule extends Rule {

	private final RegExpSource begin;
	public final List<@Nullable CaptureRule> beginCaptures;
	public final List<@Nullable CaptureRule> whileCaptures;
	private final RegExpSource _while;
	public final boolean whileHasBackReferences;
	final boolean hasMissingPatterns;
	final RuleId[] patterns;

	@Nullable
	private RegExpSourceList cachedCompiledPatterns;

	@Nullable
	private RegExpSourceList cachedCompiledWhilePatterns;

	BeginWhileRule(final RuleId id, @Nullable final String name, @Nullable final String contentName,
			final String begin, final List<@Nullable CaptureRule> beginCaptures,
			final String _while, final List<@Nullable CaptureRule> whileCaptures,
			final CompilePatternsResult patterns) {
		super(/* $location, */id, name, contentName);
		this.begin = new RegExpSource(begin, this.id);
		this.beginCaptures = beginCaptures;
		this.whileCaptures = whileCaptures;
		this._while = new RegExpSource(_while, RuleId.WHILE_RULE);
		this.whileHasBackReferences = this._while.hasBackReferences;
		this.patterns = patterns.patterns;
		this.hasMissingPatterns = patterns.hasMissingPatterns;
	}

	public String getWhileWithResolvedBackReferences(final CharSequence lineText, final OnigCaptureIndex[] captureIndices) {
		return this._while.resolveBackReferences(lineText, captureIndices);
	}

	@Override
	public void collectPatterns(final IRuleRegistry grammar, final RegExpSourceList out) {
		out.add(this.begin);
	}

	@Override
	public CompiledRule compile(final IRuleRegistry grammar, @Nullable final String endRegexSource) {
		return getCachedCompiledPatterns(grammar).compile();
	}

	@Override
	public CompiledRule compileAG(final IRuleRegistry grammar, @Nullable final String endRegexSource,
			final boolean allowA, final boolean allowG) {
		return getCachedCompiledPatterns(grammar).compileAG(allowA, allowG);
	}

	private RegExpSourceList getCachedCompiledPatterns(final IRuleRegistry grammar) {
		var cachedCompiledPatterns = this.cachedCompiledPatterns;
		if (cachedCompiledPatterns == null) {
			cachedCompiledPatterns = new RegExpSourceList();
			this.cachedCompiledPatterns = cachedCompiledPatterns;

			for (final var pattern : this.patterns) {
				final var rule = grammar.getRule(pattern);
				rule.collectPatterns(grammar, cachedCompiledPatterns);
			}
		}
		return cachedCompiledPatterns;
	}

	public CompiledRule compileWhile(@Nullable final String endRegexSource) {
		return getCachedCompiledWhilePatterns(endRegexSource).compile();
	}

	public CompiledRule compileWhileAG(@Nullable final String endRegexSource,
			final boolean allowA, final boolean allowG) {
		return getCachedCompiledWhilePatterns(endRegexSource).compileAG(allowA, allowG);
	}

	private RegExpSourceList getCachedCompiledWhilePatterns(@Nullable final String endRegexSource) {
		var cachedCompiledWhilePatterns = this.cachedCompiledWhilePatterns;
		if (cachedCompiledWhilePatterns == null) {
			cachedCompiledWhilePatterns = new RegExpSourceList();
			cachedCompiledWhilePatterns.add(this.whileHasBackReferences ? this._while.clone() : this._while);
			this.cachedCompiledWhilePatterns = cachedCompiledWhilePatterns;
		}
		if (whileHasBackReferences) {
			cachedCompiledWhilePatterns.setSource(0, defaultIfNull(endRegexSource, "\uFFFF"));
		}
		return cachedCompiledWhilePatterns;
	}
}
