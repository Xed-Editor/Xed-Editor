package io.github.rosemoe.sora.interfaces;

import android.view.KeyEvent;

import io.github.rosemoe.sora.event.EditorKeyEvent;
import io.github.rosemoe.sora.event.KeyBindingEvent;
import io.github.rosemoe.sora.widget.CodeEditor;

public interface KeyEventHandler {
    boolean onKeyEvent(CodeEditor editor,KeyEvent event, EditorKeyEvent editorKeyEvent, KeyBindingEvent keybindingEvent, int keyCode, boolean isShiftPressed, boolean isAltPressed, boolean isCtrlPressed);
}
