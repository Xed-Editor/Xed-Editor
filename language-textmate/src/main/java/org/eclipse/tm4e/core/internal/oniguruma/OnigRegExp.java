/**
 * Copyright (c) 2015-2017 Angelo ZERR.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Initial code from https://github.com/atom/node-oniguruma
 * Initial copyright Copyright (c) 2013 GitHub Inc.
 * Initial license: MIT
 * <p>
 * Contributors:
 * - GitHub Inc.: Initial code, written in JavaScript, licensed under MIT license
 * - Angelo Zerr <angelo.zerr@gmail.com> - translation and adaptation to Java
 * - Fabio Zadrozny <fabiofz@gmail.com> - Convert uniqueId to Object (for identity compare)
 * - Fabio Zadrozny <fabiofz@gmail.com> - Fix recursion error on creation of OnigRegExp with unicode chars
 */
package org.eclipse.tm4e.core.internal.oniguruma;


import java.nio.charset.StandardCharsets;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.utils.StringUtils;
import org.jcodings.specific.UTF8Encoding;
import org.joni.Matcher;
import org.joni.Option;
import org.joni.Regex;
import org.joni.Region;
import org.joni.Syntax;
import org.joni.WarnCallback;
import org.joni.exception.SyntaxException;

import io.github.rosemoe.sora.util.Logger;

/**
 * @see <a href="https://github.com/atom/node-oniguruma/blob/master/src/onig-reg-exp.cc">
 *      github.com/atom/node-oniguruma/blob/master/src/onig-reg-exp.cc</a>
 */
public final class OnigRegExp {

    @Nullable
    private OnigString lastSearchString;

    private int lastSearchPosition = -1;

    @Nullable
    private OnigResult lastSearchResult;

    private final String pattern;
    private final Regex regex;

    private final boolean hasGAnchor;

    /**
     * @throws TMException if parsing fails
     */
    public OnigRegExp(final String pattern) {
        this(pattern, false);
    }

    /**
     * @throws TMException if parsing fails
     */
    public OnigRegExp(final String pattern, final boolean ignoreCase) {
        this.pattern = pattern;
        hasGAnchor = pattern.contains("\\G");
        final byte[] patternBytes = pattern.getBytes(StandardCharsets.UTF_8);
        try {
            int options = Option.CAPTURE_GROUP;
            if (ignoreCase)
                options |= Option.IGNORECASE;
            regex = new Regex(patternBytes, 0, patternBytes.length, options, UTF8Encoding.INSTANCE, Syntax.DEFAULT,
                    /*LOGGER.isLoggable(Level.WARNING) ? LOGGER_WARN_CALLBACK :*/ WarnCallback.NONE);
        } catch (final SyntaxException ex) {
            throw new TMException("Parsing regex pattern \"" + pattern + "\" failed with " + ex, ex);
        }
    }

    /**
     * @return null if not found
     */
    public @Nullable OnigResult search(final OnigString str, final int startPosition) {
        if (hasGAnchor) {
            // Should not use caching, because the regular expression
            // targets the current search position (\G)
            return search(str.bytesUTF8, startPosition, str.bytesCount);
        }

        synchronized (this) {
            final var lastSearchResult0 = this.lastSearchResult;
            if (lastSearchString == str
                    && lastSearchPosition <= startPosition
                    && (lastSearchResult0 == null || lastSearchResult0.locationAt(0) >= startPosition)) {
                return lastSearchResult0;
            }
        }

        var result = search(str.bytesUTF8, startPosition, str.bytesCount);
        synchronized (this) {
            lastSearchString = str;
            lastSearchPosition = startPosition;
            lastSearchResult = result;
        }
        return lastSearchResult;
    }

    @Nullable
    private OnigResult search(final byte[] data, final int startPosition, final int end) {
        final Matcher matcher = regex.matcher(data);
        final int status = matcher.search(startPosition, end, Option.DEFAULT);
        if (status != Matcher.FAILED) {
            final Region region = matcher.getEagerRegion();
            return new OnigResult(region, -1);
        }
        return null;
    }

    public String pattern() {
        return pattern;
    }

    @Override
    public String toString() {
        return StringUtils.toString(this, sb -> {
            sb.append("pattern=").append(pattern);
        });
    }
}
