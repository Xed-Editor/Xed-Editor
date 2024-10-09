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
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * The class used to save auto complete result items.
 * For functionality, this class only manages the information to be displayed in list view.
 * You can implement {@link CompletionItem#performCompletion(CodeEditor, Content, int, int)} or
 * {@link CompletionItem#performCompletion(CodeEditor, Content, CharPosition)} to customize
 * your own completion method so that you can develop complex actions.
 * <p>
 * For the simplest usage, see {@link SimpleCompletionItem}
 *
 * @author Rosemoe
 * @see SimpleCompletionItem
 */
@SuppressWarnings("CanBeFinal")
public abstract class CompletionItem {

    /**
     * Icon for displaying in adapter
     */
    @Nullable
    public Drawable icon;

    /**
     * Text to display as title in adapter
     */
    public CharSequence label;

    /**
     * Text to display as description in adapter
     */
    public CharSequence desc;

    /**
     * The kind of this completion item. Based on the kind
     * an icon is chosen by the editor.
     */
    @Nullable
    protected CompletionItemKind kind;

    /**
     * Use for default sort
     */
    public int prefixLength = 0;

    /**
     * A string that should be used when comparing this item
     * with other items. When null the {@link #label label}
     * is used.
     */
    @Nullable
    public String sortText;

    @Nullable
    protected Object extra;

    public CompletionItem(CharSequence label) {
        this(label, null);
    }

    public CompletionItem(CharSequence label, CharSequence desc) {
        this(label, desc, null);
    }

    public CompletionItem(CharSequence label, CharSequence desc, Drawable icon) {
        this.label = label;
        this.desc = desc;
        this.icon = icon;
    }

    public CompletionItem label(CharSequence label) {
        this.label = label;
        return this;
    }

    public CompletionItem desc(CharSequence desc) {
        this.desc = desc;
        return this;
    }

    public CompletionItem kind(CompletionItemKind kind) {
        this.kind = kind;
        return this;
    }

    public CompletionItem icon(Drawable icon) {
        this.icon = icon;
        return this;
    }

    /**
     * Perform this completion.
     * You can implement custom logic to make your completion better(by updating selection and text
     * from here).
     * To make it considered as a single action, the editor will enter batch edit state before invoking
     * this method. Feel free to update the text by multiple calls to {@code text}.
     *
     * @param editor   The editor. You can set cursor position with that.
     * @param text     The text in editor. You can make modifications to it.
     * @param position The requested completion position (the one passed to completion thread)
     */
    public void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, @NonNull CharPosition position) {
        performCompletion(editor, text, position.line, position.column);
    }

    /**
     * Perform this completion.
     * You can implement custom logic to make your completion better(by updating selection and text
     * from here).
     * To make it considered as a single action, the editor will enter batch edit state before invoking
     * this method. Feel free to update the text by multiple calls to {@code text}.
     *
     * @param editor The editor. You can set cursor position with that.
     * @param text   The text in editor. You can make modifications to it.
     * @param line   The auto-completion line
     * @param column The auto-completion column
     * @see #performCompletion(CodeEditor, Content, CharPosition) Editor calls this method to do completion
     */
    public abstract void performCompletion(@NonNull CodeEditor editor, @NonNull Content text, int line, int column);

}

