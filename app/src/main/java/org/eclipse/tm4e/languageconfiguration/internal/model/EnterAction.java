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

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.utils.StringUtils;

/**
 * Describes what to do when pressing Enter.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode/blob/8e2ec5a7ee1ae5500c645c05145359f2a814611c/src/vs/editor/common/languages/languageConfiguration.ts#L232">
 *      github.com/microsoft/vscode/blob/main/src/vs/editor/common/languages/languageConfiguration.ts#L232</a>
 */
public class EnterAction {

	public enum IndentAction {
		/**
		 * Insert new line and copy the previous line's indentation.
		 */
		None,
		/**
		 * Insert new line and indent once (relative to the previous line's
		 * indentation).
		 */
		Indent,
		/**
		 * Insert two new lines: - the first one indented which will hold the cursor -
		 * the second one at the same indentation level
		 */
		IndentOutdent,
		/**
		 * Insert new line and outdent once (relative to the previous line's
		 * indentation).
		 */
		Outdent;

		public static IndentAction get(final @Nullable String value) {
			// see
			// https://github.com/microsoft/vscode/blob/13ba7bb446a638d37ebccb1a7d74e31c32bb9790/src/vs/workbench/contrib/codeEditor/browser/languageConfigurationExtensionPoint.ts#L341
			if (value == null) {
				return IndentAction.None;
			}
			switch (value) {
				case "none":
					return IndentAction.None;
				case "indent":
					return IndentAction.Indent;
				case "indentOutdent":
					return IndentAction.IndentOutdent;
				case "outdent":
					return IndentAction.Outdent;
				default:
					return IndentAction.None;
			}
		}
	}

	/**
	 * Describe what to do with the indentation.
	 */
	public final IndentAction indentAction;

	/**
	 * Describes text to be appended after the new line and after the indentation.
	 */
	public @Nullable String appendText;

	/**
	 * Describes the number of characters to remove from the new line's indentation.
	 */
	public final @Nullable Integer removeText;

	public EnterAction(final IndentAction indentAction) {
		this(indentAction, null, null);
	}

	public EnterAction(final IndentAction indentAction, final @Nullable String appendText, final @Nullable Integer removeText) {
		this.indentAction = indentAction;
		this.appendText = appendText;
		this.removeText = removeText;
	}

	@Override
	public String toString() {
		return StringUtils.toString(this, sb -> sb
				.append("indentAction=").append(indentAction).append(", ")
				.append("appendText=").append(appendText).append(", ")
				.append("removeText=").append(removeText));
	}
}
