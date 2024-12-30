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
package org.eclipse.tm4e.core.internal.theme.raw;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.theme.IThemeSetting;

/**
 * A single theme setting.
 *
 * @see <a href="https://github.com/microsoft/vscode-textmate/blob/b6bbee8d53c029d9279a0c9a998b78f05247d6d1/src/theme.ts#L91">
 *      github.com/microsoft/vscode-textmate/blob/main/src/theme.ts</a>
 */
public interface IRawThemeSetting {

	@Nullable
	String getName();

	@Nullable
	Object getScope();

	@Nullable
	IThemeSetting getSetting();

}
