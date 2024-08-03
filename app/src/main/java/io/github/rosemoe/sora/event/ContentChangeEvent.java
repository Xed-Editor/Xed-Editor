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
package io.github.rosemoe.sora.event;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * This event happens when {@link CodeEditor#setText(CharSequence)} is called or
 * user edited the displaying content.
 * <p>
 * Note that you should not update the content at this time. Otherwise, there might be some
 * exceptions causing the editor framework to crash. If you do need to update the content, you should
 * post your actions to the main thread so that the user's modification will be successful.
 *
 * @author Rosemoe
 */
public class ContentChangeEvent extends Event {

    /**
     * Notify that {@link CodeEditor#setText(CharSequence)} is called
     */
    public final static int ACTION_SET_NEW_TEXT = 1;

    /**
     * Notify that user inserted some texts to the content
     */
    public final static int ACTION_INSERT = 2;

    /**
     * Notify that user deleted some texts in the content
     */
    public final static int ACTION_DELETE = 3;

    private final int action;
    private final CharPosition start;
    private final CharPosition end;
    private final CharSequence textChanged;
    private final boolean causedByUndoManager;

    public ContentChangeEvent(@NonNull CodeEditor editor, int action, @NonNull CharPosition changeStart, @NonNull CharPosition changeEnd, @NonNull CharSequence textChanged, boolean causeByUndoManager) {
        super(editor);
        this.action = action;
        start = changeStart;
        end = changeEnd;
        this.textChanged = textChanged;
        causedByUndoManager = causeByUndoManager;
    }

    /**
     * Get action code of the event.
     *
     * @see #ACTION_SET_NEW_TEXT
     * @see #ACTION_INSERT
     * @see #ACTION_DELETE
     */
    public int getAction() {
        return action;
    }

    /**
     * Return the CharPosition indicating the start of changed region.
     * <p>
     * Note that you can not modify the values in the returned instance.
     */
    @NonNull
    public CharPosition getChangeStart() {
        return start;
    }

    /**
     * Return the CharPosition indicating the end of changed region.
     * <p>
     * Note that you can not modify the values in the returned instance.
     */
    @NonNull
    public CharPosition getChangeEnd() {
        return end;
    }

    /**
     * Return the changed text in this modification.
     * If action is {@link #ACTION_SET_NEW_TEXT}, Content instance is returned.
     * If action is {@link #ACTION_INSERT}, inserted text is returned.
     * If action is {@link #ACTION_DELETE}, deleted text is returned.
     */
    @NonNull
    public CharSequence getChangedText() {
        return textChanged;
    }

    /**
     * If the content change is caused by undo/redo
     */
    public boolean isCausedByUndoManager() {
        return causedByUndoManager;
    }
}
