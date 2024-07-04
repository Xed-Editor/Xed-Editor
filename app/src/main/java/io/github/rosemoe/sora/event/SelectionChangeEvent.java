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
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.EditorSearcher;

/**
 * This event happens when text is edited by the user, or the user click the view to change the
 * position of selection. Even when the actual values of CharPosition are not changed, you may receive the event.
 * <p>
 * Note that you should not change returned CharPosition objects because they are shared in an event
 * dispatch.
 */
public class SelectionChangeEvent extends Event {

    /**
     * Unknown cause
     */
    public final static int CAUSE_UNKNOWN = 0;
    /**
     * Selection change caused by text modifications
     */
    public final static int CAUSE_TEXT_MODIFICATION = 1;
    /**
     * Set selection by handle
     */
    public final static int CAUSE_SELECTION_HANDLE = 2;
    /**
     * Set selection by single tap
     */
    public final static int CAUSE_TAP = 3;
    /**
     * Set selection because of {@link android.view.inputmethod.InputConnection#setSelection(int, int)}
     */
    public final static int CAUSE_IME = 4;
    /**
     * Long press
     */
    public final static int CAUSE_LONG_PRESS = 5;
    /**
     * Search text by {@link EditorSearcher}
     */
    public final static int CAUSE_SEARCH = 6;
    /**
     * From keyboard or direct method invocation to change selection
     */
    public final static int CAUSE_KEYBOARD_OR_CODE = 7;
    /**
     * From mouse
     */
    public final static int CAUSE_MOUSE_INPUT = 8;
    @Nullable
    private final CharPosition oldLeft;
    @Nullable
    private final CharPosition oldRight;
    private final CharPosition left;
    private final CharPosition right;
    private final int cause;

    public SelectionChangeEvent(@NonNull CodeEditor editor, @Nullable CharPosition oldLeft, @Nullable CharPosition oldRight, int cause) {
        super(editor);
        this.oldLeft = oldLeft;
        this.oldRight = oldRight;
        var cursor = editor.getText().getCursor();
        left = cursor.left();
        right = cursor.right();
        this.cause = cause;
    }

    /**
     * Get cause of the change
     *
     * @see #CAUSE_UNKNOWN
     * @see #CAUSE_TEXT_MODIFICATION
     * @see #CAUSE_SELECTION_HANDLE
     * @see #CAUSE_TAP
     * @see #CAUSE_IME
     * @see #CAUSE_LONG_PRESS
     * @see #CAUSE_SEARCH
     */
    public int getCause() {
        return cause;
    }

    /**
     * Get the last left selection's position before changed
     */
    @Nullable
    public CharPosition getOldLeft() {
        return oldLeft;
    }

    /**
     * Get the last right selection's position before changed
     */
    @Nullable
    public CharPosition getOldRight() {
        return oldRight;
    }

    /**
     * Get the left selection's position
     */
    @NonNull
    public CharPosition getLeft() {
        return left;
    }

    /**
     * Get the right selection's position
     */
    @NonNull
    public CharPosition getRight() {
        return right;
    }

    /**
     * Checks whether text is selected
     */
    public boolean isSelected() {
        return left.index != right.index;
    }

}
