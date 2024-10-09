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
package org.eclipse.tm4e.core.internal.theme;

import org.eclipse.jdt.annotation.Nullable;

public interface IThemeSetting {

	@Nullable
	Object getFontStyle();

	@Nullable
	String getBackground();

	@Nullable
	String getForeground();
}
