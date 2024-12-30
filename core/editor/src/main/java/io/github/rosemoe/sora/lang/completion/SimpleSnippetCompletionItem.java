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
package io.github.rosemoe.sora.lang.completion;

import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

public class SimpleSnippetCompletionItem extends CompletionItem {

    private final SnippetDescription snippet;

    public SimpleSnippetCompletionItem(CharSequence label, SnippetDescription snippet) {
        this(label, null, snippet);
    }

    public SimpleSnippetCompletionItem(CharSequence label, CharSequence desc, SnippetDescription snippet) {
        this(label, desc, null, snippet);
    }

    public SimpleSnippetCompletionItem(CharSequence label, CharSequence desc, Drawable icon, SnippetDescription snippet) {
        super(label, desc, icon);
        this.snippet = snippet;
        kind(CompletionItemKind.Snippet);
    }



    @Override
    public void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, @NonNull CharPosition position) {
        int prefixLength = snippet.getSelectedLength();
        var selectedText = text.subSequence(position.index - prefixLength, position.index).toString();
        int actionIndex = position.index;
        if (snippet.getDeleteSelected()) {
            text.delete(position.index - prefixLength, position.index);
            actionIndex -= prefixLength;
        }
        editor.getSnippetController().startSnippet(actionIndex, snippet.getSnippet(), selectedText);
    }

    @Override
    public void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, int line, int column) {
        // do nothing
    }
}
