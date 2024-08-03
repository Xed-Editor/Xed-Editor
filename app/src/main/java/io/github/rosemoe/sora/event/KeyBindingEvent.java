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

import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Keybinding event in editor.
 *
 * <p> This is different from {@link EditorKeyEvent}.
 * An {@code EditorKeyEvent} is dispatched by the editor whenever there is a key event.
 * However, a {@code KeyBindingEvent} is dispatched only for keybindings i.e.
 * when multiple keys are pressed at once.
 * For example, <b>Ctrl + X, Ctrl + D, Ctrl + Alt + O, etc.</b>
 * </p>
 *
 * <p>
 * This event is dispatched <strong>after</strong> the {@link EditorKeyEvent}.
 * So, if any {@code EditorKeyEvent} consumes the event (sets the {@link InterceptTarget#TARGET_EDITOR} flag),
 * {@code KeyBindingEvent} will not be called.
 * </p>
 *
 * @author Akash Yadav
 */
public class KeyBindingEvent extends EditorKeyEvent {

    private final boolean editorAbleToHandle;

    /**
     * Creates a new {@code KeyBindingEvent} instance.
     *
     * @param editor             The editor.
     * @param src                The source {@link KeyEvent}.
     * @param type               The key event type.
     * @param editorAbleToHandle <code>true</code> if the editor can handle this event, <code>false</code> otherwise.
     */
    public KeyBindingEvent(@NonNull CodeEditor editor, @NonNull KeyEvent src, Type type, boolean editorAbleToHandle) {
        super(editor, src, type);
        this.editorAbleToHandle = editorAbleToHandle;
    }

    /**
     * Is the editor capable of handling this key binding event?
     *
     * @return <code>true</code> if the editor can handle this event. <code>false</code> otherwise.
     */
    public boolean canEditorHandle() {
        return this.editorAbleToHandle;
    }

}
