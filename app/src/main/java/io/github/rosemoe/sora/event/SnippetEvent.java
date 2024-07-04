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

import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * Notify that snippet controller state is changed.
 * <br/>
 * If action is {@link #ACTION_START} and any event receiver intercepts editor, the snippet edit will
 * stop before moving to any tab stop. And consequently, a {@link SnippetEvent} with action {@link #ACTION_STOP}
 * will be broadcast immediately.
 * <br/>
 * There is at least one tab stop in the list when action is {@link #ACTION_START} or {@link #ACTION_SHIFT}.
 * But no tab stop is left there when action is {@link #ACTION_STOP}. The last tab stop is where the selection
 * will be placed when the snippet is finished normally.
 *
 * @author Rosemoe
 */
public class SnippetEvent extends Event {

    /**
     * Called before controller shifts to any tab stop
     */
    public final static int ACTION_START = 1;
    /**
     * Called when controller shifted to a tab stop
     */
    public final static int ACTION_SHIFT = 2;
    /**
     * Called when controller <strong>has exited</strong> a snippet
     */
    public final static int ACTION_STOP = 3;

    private final int action;
    private final int currentTabStop;
    private final int totalTabStop;

    public SnippetEvent(@NonNull CodeEditor editor, int action, int currentTabStop, int totalTabStop) {
        super(editor);
        this.action = action;
        this.currentTabStop = currentTabStop;
        this.totalTabStop = totalTabStop;
    }

    /**
     * @see #ACTION_START
     * @see #ACTION_SHIFT
     * @see #ACTION_STOP
     */
    public int getAction() {
        return action;
    }

    /**
     * Get the current index of tab stops
     */
    public int getCurrentTabStop() {
        return currentTabStop;
    }

    /**
     * Get the count of tab stops
     */
    public int getTotalTabStop() {
        return totalTabStop;
    }
}
