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

import io.github.rosemoe.sora.text.ICUUtils;
import io.github.rosemoe.sora.util.IntPair;
import io.github.rosemoe.sora.widget.CodeEditor;

public class EditorBasedSnippetVariableResolver implements ISnippetVariableResolver {

    private final CodeEditor editor;

    public EditorBasedSnippetVariableResolver(@NonNull CodeEditor editor) {
        this.editor = editor;
    }

    @NonNull
    @Override
    public String[] getResolvableNames() {
        return new String[]{
                "TM_CURRENT_LINE", "TM_LINE_INDEX", "TM_LINE_NUMBER", "CURSOR_INDEX", "CURSOR_NUMBER",
                "TM_CURRENT_WORD", "SELECTION", "TM_SELECTED_TEXT"
        };
    }

    @NonNull
    @Override
    public String resolve(@NonNull String name) {
        switch (name) {
            case "TM_CURRENT_LINE":
            case "TM_LINE_NUMBER":
                return Integer.toString(editor.getCursor().getLeftLine() + 1);
            case "TM_LINE_INDEX":
                return Integer.toString(editor.getCursor().getLeftLine());
            case "CURSOR_INDEX":
                return Integer.toString(editor.getCursor().getLeft());
            case "CURSOR_NUMBER":
                return Integer.toString(editor.getCursor().getLeft() + 1);
            case "TM_CURRENT_WORD": {
                var text = editor.getText();
                var res = ICUUtils.getWordRange(text.getLine(text.getCursor().getLeftLine()), text.getCursor().getLeftColumn(), true);
                return text.getLine(text.getCursor().getLeftLine()).subSequence(IntPair.getFirst(res), IntPair.getSecond(res)).toString();
            }
            case "SELECTION":
            case "TM_SELECTED_TEXT": {
                var cursor = editor.getCursor();
                return editor.getText().subSequence(cursor.getLeft(), cursor.getRight()).toString();
            }
        }
        throw new IllegalArgumentException("Unsupported variable name:" + name);
    }
}
