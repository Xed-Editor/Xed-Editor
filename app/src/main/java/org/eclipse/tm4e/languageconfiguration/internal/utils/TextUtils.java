/**
 * Copyright (c) 2015-2022 Angelo ZERR and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Contributors:
 * Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package org.eclipse.tm4e.languageconfiguration.internal.utils;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.util.IntPair;

public final class TextUtils {

    /**
     * @return true if text of the command is an enter and false otherwise.
     */
    /*public static boolean isEnter(final IDocument doc, final DocumentCommand cmd) {
        return cmd.length == 0 && cmd.text != null && TextUtilities.equals(doc.getLegalLineDelimiters(), cmd.text) != -1;
    }*/
    public static String normalizeIndentation(final String text, final int tabSize, final boolean insertSpaces) {
        int firstNonWhitespaceIndex = firstNonWhitespaceIndex(text);
        if (firstNonWhitespaceIndex == -1) {
            firstNonWhitespaceIndex = text.length();
        }
        return normalizeIndentationFromWhitespace(text.substring(0, firstNonWhitespaceIndex), tabSize, insertSpaces)
                + text.substring(firstNonWhitespaceIndex);
    }

    private static String normalizeIndentationFromWhitespace(final String text, final int tabSize, final boolean insertSpaces) {
        int spacesCnt = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\t') {
                spacesCnt += tabSize;
            } else {
                spacesCnt++;
            }
        }

        final var result = new StringBuilder();
        if (!insertSpaces) {
            final long tabsCnt = spacesCnt / tabSize;
            spacesCnt = spacesCnt % tabSize;
            for (int i = 0; i < tabsCnt; i++) {
                result.append('\t');
            }
        }

        for (int i = 0; i < spacesCnt; i++) {
            result.append(' ');
        }

        return result.toString();
    }

    /**
     * Returns the start of the string at the offset in the text. If the string is not in the text at the offset, returns -1.
     * <p>
     * Example:
     *
     * <pre>
     * text="apple banana", offset=8, string="banana" -> returns 6
     * </pre>
     */
    public static int startIndexOfOffsetTouchingString(final String text, final int offset, final String string) {
        int start = offset - string.length();
        start = start < 0 ? 0 : start;
        int end = offset + string.length();
        end = end >= text.length() ? text.length() : end;
        try {
            final int indexInSubtext = text.substring(start, end).indexOf(string);
            return indexInSubtext == -1 ? -1 : start + indexInSubtext;
        } catch (final IndexOutOfBoundsException e) {
            return -1;
        }
    }

    /**
     * Returns first index of the string that is not whitespace. If string is empty
     * or contains only whitespaces, returns -1
     */
    private static int firstNonWhitespaceIndex(final String text) {
        for (int i = 0, len = text.length(); i < len; i++) {
            final char c = text.charAt(i);
            if (c != ' ' && c != '\t') {
                return i;
            }
        }
        return -1;
    }

    public static String getIndentationFromWhitespace(final String whitespace, final int tabSize, final boolean insertSpaces) {
        final var tab = "\t"; //$NON-NLS-1$
        int indentOffset = 0;
        boolean startsWithTab = true;
        boolean startsWithSpaces = true;
        final String spaces = insertSpaces
                ? " ".repeat(tabSize)
                : "";
        while (startsWithTab || startsWithSpaces) {
            startsWithTab = whitespace.startsWith(tab, indentOffset);
            startsWithSpaces = insertSpaces && whitespace.startsWith(spaces, indentOffset);
            if (startsWithTab) {
                indentOffset += tab.length();
            }
            if (startsWithSpaces) {
                indentOffset += spaces.length();
            }
        }
        return whitespace.substring(0, indentOffset);
    }

    public static String getIndentationAtPosition(final Content doc, final int offset) {
        try {
            // find start offset of current line
            final int lineStartOffset = doc.getIndexer().getCharPosition(offset).index;

            // find white spaces
            final int indentationEndOffset = findEndOfWhiteSpace(doc, lineStartOffset, offset);

            return doc.substring(lineStartOffset, indentationEndOffset - lineStartOffset);
        } catch (final Exception excp) {
            // stop work
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * Returns the first offset greater than <code>offset</code> and smaller than
     * <code>end</code> whose character is not a space or tab character. If no such
     * offset is found, <code>end</code> is returned.
     *
     * @param doc     the document to search in
     * @param startAt the offset at which searching start
     * @param endAt   the offset at which searching stops
     * @return the offset in the specified range whose character is not a space or tab
     */
    private static int findEndOfWhiteSpace(final Content doc, int startAt, final int endAt) {
        while (startAt < endAt) {
            final char c = doc.charAt(startAt);
            if (c != ' ' && c != '\t') {
                return startAt;
            }
            startAt++;
        }
        return endAt;
    }

    /**
     * Determines if all the characters at any offset of the specified document line are the whitespace characters.
     *
     * @param doc  the document to search in
     * @param line zero-based document line number
     * @return <code>true</code> if all the characters of the specified document line are the whitespace
     * characters, otherwise returns <code>false</code>
     */
    public static boolean isBlankLine(final Content doc, final int line) {
        try {
            int offset = doc.charAt(line, 0);
            final int lineEnd = offset + doc.getLine(line).length();
            while (offset < lineEnd) {
                if (!Character.isWhitespace(doc.charAt(offset))) {
                    return false;
                }
                offset++;
            }
        } catch (final Exception e) {
            // Ignore, forcing a positive result
        }
        return true;
    }

    /**
     * Returns the leading whitespace of the string.
     * If the string contains only whitespaces, returns entire string
     */
    public static String getLeadingWhitespace(String str, int start, int end) {
        for (var i = start; i < end; i++) {
            var chCode = str.charAt(i);
            if (chCode != 32 /*CharCode.Space*/ && chCode != 9/*CharCode.Tab*/) {
                return str.substring(start, i);
            }
        }
        return str.substring(start, end);
    }

    public static String getLinePrefixingWhitespaceAtPosition(final Content d, final CharPosition position) {

        var line = d.getLine(position.line);

        var startIndex = IntPair.getFirst(io.github.rosemoe.sora.text.TextUtils.findLeadingAndTrailingWhitespacePos(
                line
        ));

        return line.subSequence(0, startIndex).toString();

    }

    /**
     * Returns the leading whitespace of the string.
     * If the string contains only whitespaces, returns entire string
     */
    public static String getLeadingWhitespace(String str) {
        return getLeadingWhitespace(str, 0, str.length());
    }


    private TextUtils() {
    }

}
