/**
 * Copyright (c) 2018 Red Hat Inc. and others.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 * <p>
 * SPDX-License-Identifier: EPL-2.0
 * <p>
 * Contributors:
 * Lucas Bullen (Red Hat Inc.) - initial API and implementation
 */
package org.eclipse.tm4e.languageconfiguration.internal.supports;

import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tm4e.languageconfiguration.internal.model.CharacterPair;
import org.eclipse.tm4e.languageconfiguration.internal.model.CommentRule;

import io.github.rosemoe.sora.text.Content;

public final class CommentSupport {

    @Nullable
    private final CommentRule comments;

    public CommentSupport(@Nullable final CommentRule comments) {
        this.comments = comments;
    }

	private boolean isInComment(final Content text, final int offset) {
		try {
			if (isInBlockComment(text.subSequence(0, offset).toString())) {
				return true;
			}
			var indexer = text.getIndexer();
			final int line = indexer.getCharLine(offset);
			final int lineOffset = indexer.getCharIndex(line,0);
			return isInLineComment(text.subSequence(lineOffset, offset - lineOffset).toString());
		} catch (final Exception e) {
			return false;
		}
	}

    @Nullable
    public String getLineComment() {
        final var comments = this.comments;
        return comments == null ? null : comments.lineComment;
    }

    @Nullable
    public CharacterPair getBlockComment() {
        final var comments = this.comments;
        return comments == null ? null : comments.blockComment;
    }

    private boolean isInLineComment(final String indexLinePrefix) {
        final var comments = this.comments;
        if (comments == null)
            return false;
        return indexLinePrefix.indexOf(comments.lineComment) != -1;
    }

    private boolean isInBlockComment(final String indexPrefix) {
        final var comments = this.comments;
        if (comments == null)
            return false;

        final var blockComment = comments.blockComment;
        if (blockComment == null)
            return false;

        final String commentOpen = blockComment.open;
        final String commentClose = blockComment.close;
        int index = indexPrefix.indexOf(commentOpen);
        while (index != -1 && index < indexPrefix.length()) {
            final int closeIndex = indexPrefix.indexOf(commentClose, index + commentOpen.length());
            if (closeIndex == -1) {
                return true;
            }
            index = indexPrefix.indexOf(commentOpen, closeIndex + commentClose.length());
        }
        return false;
    }
}
