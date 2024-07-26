/**
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 * Lucas Bullen (Red Hat Inc.) - initial API and implementation
 */
package org.eclipse.tm4e.languageconfiguration.internal.model;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * Describes how comments for a language work.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/languageConfiguration.ts#L13">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/languageConfiguration.ts#L13</a>
 */
public final class CommentRule {

	/**
	 * The line comment token, like `// this is a comment`
	 */
	@Nullable
	public final String lineComment;

	/**
	 * The block comment character pair, like `/* block comment *&#47;`
	 */
	@Nullable
	public final CharacterPair blockComment;

	public CommentRule(@Nullable final String lineComment, @Nullable final CharacterPair blockComment) {
		this.lineComment = lineComment;
		this.blockComment = blockComment;
	}

	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> sb
				.append("lineComment=").append(lineComment).append(", ")
				.append("blockComment=").append(blockComment));
	}
}
