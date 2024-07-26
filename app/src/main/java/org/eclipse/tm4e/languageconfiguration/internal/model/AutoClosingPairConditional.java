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
package org.eclipse.tm4e.languageconfiguration.internal.model;

import androidx.annotation.NonNull;

import java.util.List;

import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/languageConfiguration.ts#L201">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/languageConfiguration.ts#L201</a>
 */
public final class AutoClosingPairConditional extends AutoClosingPair {

	public final List<String> notIn;

	public AutoClosingPairConditional(final String open, final String close, final List<String> notIn) {
		super(open, close);
		this.notIn = notIn;
	}

	@NonNull
	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> sb
				.append("open=").append(open).append(", ")
				.append("close=").append(close).append(", ")
				.append("notIn=").append(notIn));
	}
}
