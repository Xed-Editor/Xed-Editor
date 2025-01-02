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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.oniguruma.OnigCaptureIndex;
import org.eclipse.tm4e.core.internal.utils.RegexSource;
import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/rule.ts#L43">
 *      github.com/microsoft/vscode-textmate/blob/main/src/rule.ts</a>
 */
public abstract class Rule {

	final RuleId id;

	@Nullable
	private final String name;
	private final boolean nameIsCapturing;

	@Nullable
	private final String contentName;
	private final boolean contentNameIsCapturing;

	Rule(final RuleId id, @Nullable final String name, final @Nullable String contentName) {
		this.id = id;
		this.name = name;
		this.nameIsCapturing = RegexSource.hasCaptures(name);
		this.contentName = contentName;
		this.contentNameIsCapturing = RegexSource.hasCaptures(contentName);
	}

	@Nullable
	public String getName(@Nullable final CharSequence lineText, final OnigCaptureIndex @Nullable [] captureIndices) {
		final var name = this.name;
		if (!nameIsCapturing || name == null || lineText == null || captureIndices == null) {
			return name;
		}
		return RegexSource.replaceCaptures(name, lineText, captureIndices);
	}

	@Nullable
	public String getContentName(final CharSequence lineText, final OnigCaptureIndex[] captureIndices) {
		final var contentName = this.contentName;
		if (!contentNameIsCapturing || contentName == null) {
			return contentName;
		}
		return RegexSource.replaceCaptures(contentName, lineText, captureIndices);
	}

	public abstract void collectPatterns(IRuleRegistry grammar, RegExpSourceList out);

	public abstract CompiledRule compile(IRuleRegistry grammar, @Nullable String endRegexSource);

	public abstract CompiledRule compileAG(IRuleRegistry grammar, @Nullable String endRegexSource, boolean allowA, boolean allowG);

	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> {
			sb.append("id=").append(id);
			sb.append(",name=").append(name);
		});
	}
}
