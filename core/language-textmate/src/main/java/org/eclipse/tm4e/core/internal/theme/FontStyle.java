/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.core.internal.theme;

/**
 * Font style definitions.
 *
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/theme.ts#L306">
 *      https://github.com/microsoft/vscode-textmate/blob/main/src/theme.ts</a>
 */
public final class FontStyle {

    public static final int NotSet = -1;

    // This can are bit-flags, so it can be `Italic | Bold`
    public static final int None = 0;
    public static final int Italic = 1;
    public static final int Bold = 2;
    public static final int Underline = 4;
    public static final int Strikethrough = 8;

    public static String fontStyleToString(final int fontStyle) {
        if (fontStyle == NotSet) {
            return "not set";
        }
        if (fontStyle == None) {
            return "none";
        }

        final var style = new StringBuilder();
        if ((fontStyle & Italic) == Italic) {
            style.append("italic ");
        }
        if ((fontStyle & Bold) == Bold) {
            style.append("bold ");
        }
        if ((fontStyle & Underline) == Underline) {
            style.append("underline ");
        }
        if ((fontStyle & Strikethrough) == Strikethrough) {
            style.append("strikethrough ");
        }
        // String.isEmpty() no available in android
        if (style.length() < 1) {
            return "none";
        }
        style.setLength(style.length() - 1);
        return style.toString();
    }

    private FontStyle() {
    }
}
