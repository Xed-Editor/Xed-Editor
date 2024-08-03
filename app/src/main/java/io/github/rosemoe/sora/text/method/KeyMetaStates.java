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
package io.github.rosemoe.sora.text.method;

import android.text.Editable;
import android.view.KeyEvent;

import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Handles key events such as SHIFT
 *
 * @author Rosemoe
 */
public class KeyMetaStates extends android.text.method.MetaKeyKeyListener {

    private final CodeEditor editor;

    /**
     * Dummy text used for Android original APIs
     */
    private final Editable dest = Editable.Factory.getInstance().newEditable("");
    private boolean isCtrlPressed = false;

    public KeyMetaStates(CodeEditor editor) {
        this.editor = editor;
    }

    public void onKeyDown(KeyEvent event) {
        super.onKeyDown(editor, dest, event.getKeyCode(), event);
        isCtrlPressed = event.isCtrlPressed();
    }

    public void onKeyUp(KeyEvent event) {
        super.onKeyUp(editor, dest, event.getKeyCode(), event);
        isCtrlPressed = event.isCtrlPressed();
    }

    public int getMetaState(KeyEvent event) {
        return getMetaState(dest, event);
    }

    public boolean isCtrlPressed() {
        return isCtrlPressed;
    }

    public boolean isShiftPressed() {
        return getMetaState(dest, META_SHIFT_ON) != 0;
    }

    public boolean isAltPressed() {
        return getMetaState(dest, META_ALT_ON) != 0;
    }

    public boolean isSymPressed() {
        return getMetaState(dest, META_SYM_ON) != 0;
    }

    public void adjust() {
        adjustMetaAfterKeypress(dest);
    }

    public void clearMetaStates(int states) {
        clearMetaKeyState(editor, dest, states);
    }

}
