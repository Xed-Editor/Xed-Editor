/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Initial code from https://github.com/microsoft/vscode-textmate/
 * Initial copyright Copyright (C) Microsoft Corporation. All rights reserved.
 * Initial license: MIT
 * <p>
 * Contributors:
 * - Microsoft Corporation: Initial code, written in TypeScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 */
package org.eclipse.tm4e.core.internal.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.internal.oniguruma.OnigCaptureIndex;

/**
 * @see <a href=
 *      "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/utils.ts#L59">
 *      github.com/microsoft/vscode-textmate/blob/main/src/utils.ts</a>
 */
public final class RegexSource {

	/* private static final Pattern CAPTURING_REGEX_SOURCE = Pattern
		.compile("\\$(\\d+)|\\$\\{(\\d+):\\/(downcase|upcase)}");*/

    //fix for android
    private static final Pattern CAPTURING_REGEX_SOURCE = Pattern
            .compile("\\$(\\d+)|\\$\\{(\\d+):/(downcase|upcase)\\}");


    /**
     * Escapes/prefixes RegEx meta characters with a backslash in the given string.
     *
     * It is a non-regex based faster alternative to the <a href=
     * "https://github.com/microsoft/vscode-textmate/blob/88baacf1a6637c5ec08dce18cea518d935fcf0a0/src/utils.ts#L159">TypeScript
     * implementation</a>:
     *
     * <pre>
     * function escapeRegExpCharacters(value: string): string {
     *   return value.replace(/[\-\\\{\}\*\+\?\|\^\$\.\,\[\]\(\)\#\s]/g, '\\$&');
     * }
     * </pre>
     *
     * @return a string with the RegEx meta characters escaped
     */
    public static String escapeRegExpCharacters(final CharSequence value) {
        final int valueLen = value.length();
        final var sb = new StringBuilder(valueLen);
        for (int i = 0; i < valueLen; i++) {
            final char ch = value.charAt(i);
            switch (ch) {
                case '-', '\\', '{', '}', '*', '+', '?', '|', '^', '$', '.', ',', '[', ']', '(', ')', '#':
					/* escaping white space chars is actually not necessary:
					' ', '\t', '\n', '\f', '\r',
					0x0B: // vertical tab \v
					*/
                    sb.append('\\');
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    public static boolean hasCaptures(@Nullable final CharSequence regexSource) {
        if (regexSource == null) {
            return false;
        }
        return CAPTURING_REGEX_SOURCE.matcher(regexSource).find();
    }

    public static String replaceCaptures(final CharSequence regexSource, final CharSequence captureSource,
                                         final OnigCaptureIndex[] captureIndices) {
        final Matcher m = CAPTURING_REGEX_SOURCE.matcher(regexSource);
        // StringBuilder -> StringBuffer
        final var result = new StringBuffer();
        while (m.find()) {
            final String match = m.group();
            final String replacement = getReplacement(match, captureSource, captureIndices);
            m.appendReplacement(result, replacement);
        }
        m.appendTail(result);
        return result.toString();
    }

    private static String getReplacement(final String match, final CharSequence captureSource, final OnigCaptureIndex[] captureIndices) {
        final int index;
        final String command;
        final int doublePointIndex = match.indexOf(':');
        if (doublePointIndex != -1) {
            index = Integer.parseInt(match.substring(2, doublePointIndex));
            command = match.substring(doublePointIndex + 2, match.length() - 1);
        } else {
            index = Integer.parseInt(match.substring(1));
            command = null;
        }
        final OnigCaptureIndex capture = captureIndices.length > index ? captureIndices[index] : null;
        if (capture != null) {
            var result = captureSource.subSequence(capture.start, capture.end);
            // Remove leading dots that would make the selector invalid
            while (!(result.length() < 1) && result.charAt(0) == '.') {
                result = result.subSequence(1, result.length());
            }
            if ("downcase".equals(command)) {
                return result.toString().toLowerCase();
            }
            if ("upcase".equals(command)) {
                return result.toString().toUpperCase();
            }
            return result.toString();
        }
        return match;
    }

    /**
     * Helper class, access members statically
     */
    private RegexSource() {
    }
}
