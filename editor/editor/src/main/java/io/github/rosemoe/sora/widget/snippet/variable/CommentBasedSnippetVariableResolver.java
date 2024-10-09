/*
 *    sora-editor - the awesome code editor for Android
 *    https://github.com/Rosemoe/sora-editor
 *    Copyright (C) 2020-2024  Rosemoe
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 *
 *     Please contact Rosemoe by email 2073412493@qq.com if you need
 *     additional information or have any questions
 */
package io.github.rosemoe.sora.widget.snippet.variable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class CommentBasedSnippetVariableResolver implements ISnippetVariableResolver {

    private String[] commentTokens;

    public CommentBasedSnippetVariableResolver() {
        this(null);
    }

    /**
     * For example, Java language: {@code new String[]{ "//", "/*", "*\/" }
     */
    public CommentBasedSnippetVariableResolver(@Nullable String[] commentTokenStrings) {
        setCommentTokens(commentTokenStrings);
    }

    public void setCommentTokens(@Nullable String[] commentTokens) {
        this.commentTokens = commentTokens;
    }

    public String[] getCommentTokens() {
        return commentTokens;
    }

    @NonNull
    @Override
    public String[] getResolvableNames() {
        return new String[]{
                "LINE_COMMENT", "BLOCK_COMMENT_START", "BLOCK_COMMENT_END"
        };
    }

    @NonNull
    @Override
    public String resolve(@NonNull String name) {
        if (commentTokens == null || commentTokens.length != 3) {
            throw new IllegalStateException("language comment style is not configured properly");
        }
        switch (name) {
            case "LINE_COMMENT":
                return commentTokens[0];
            case "BLOCK_COMMENT_START":
                return commentTokens[1];
            case "BLOCK_COMMENT_END":
                return commentTokens[2];
        }
        throw new IllegalArgumentException("Unsupported variable name:" + name);
    }
}
