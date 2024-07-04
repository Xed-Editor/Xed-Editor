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

import android.view.KeyEvent;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.text.method.KeyMetaStates;
import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Receives key related events in editor.
 * <p>
 * You may set a boolean for editor to return to the Android KeyEvent framework.
 *
 * @author Rosemoe
 * @see ResultedEvent#setResult(Object)
 * <p>
 * This class mirrors methods of {@link KeyEvent}, but some methods are changed:
 * @see #isAltPressed()
 * @see #isShiftPressed()
 */
public class EditorKeyEvent extends ResultedEvent<Boolean> {

    private final KeyEvent src;
    private final Type type;
    private final boolean shiftPressed;
    private final boolean altPressed;

    public EditorKeyEvent(@NonNull CodeEditor editor, @NonNull KeyEvent src, @NonNull Type type) {
        super(editor);
        this.src = src;
        this.type = type;
        shiftPressed = getEditor().getKeyMetaStates().isShiftPressed();
        altPressed = getEditor().getKeyMetaStates().isAltPressed();
    }

    @Override
    public boolean canIntercept() {
        return true;
    }

    public int getAction() {
        return src.getAction();
    }

    public int getKeyCode() {
        return src.getKeyCode();
    }

    public int getRepeatCount() {
        return src.getRepeatCount();
    }

    public int getMetaState() {
        return src.getMetaState();
    }

    public int getModifiers() {
        return src.getModifiers();
    }

    public long getDownTime() {
        return src.getDownTime();
    }

    /**
     * Get the key event type.
     *
     * @return The key event type.
     */
    @NonNull
    public Type getEventType() {
        return this.type;
    }

    @Override
    public long getEventTime() {
        return src.getEventTime();
    }

    /**
     * editor change: track shift/alt by {@link KeyMetaStates}
     */
    public boolean isShiftPressed() {
        return shiftPressed;
    }

    /**
     * editor change: track shift/alt by {@link KeyMetaStates}
     */
    public boolean isAltPressed() {
        return altPressed;
    }

    public boolean isCtrlPressed() {
        return (src.getMetaState() & KeyEvent.META_CTRL_ON) != 0;
    }

    public final boolean result(boolean editorResult) {
        var userResult = isResultSet() ? getResult() : false;
        if (isIntercepted()) {
            return userResult;
        } else {
            return userResult || editorResult;
        }
    }

    /**
     * The type of {@link EditorKeyEvent}.
     */
    public enum Type {

        /**
         * Used for {@link CodeEditor#onKeyUp(int, KeyEvent)}.
         */
        UP,

        /**
         * Used for {@link CodeEditor#onKeyDown(int, KeyEvent)}.
         */
        DOWN,

        /**
         * Used for {@link CodeEditor#onKeyMultiple(int, int, KeyEvent)}.
         */
        MULTIPLE
    }
}
