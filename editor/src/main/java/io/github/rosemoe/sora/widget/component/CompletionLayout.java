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
package io.github.rosemoe.sora.widget.component;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * Manages layout of {@link EditorAutoCompletion}
 * Can be set by {@link EditorAutoCompletion#setLayout(CompletionLayout)}
 * <p>
 * The implementation of this class must call {@link EditorAutoCompletion#select(int)} to select the
 * item in completion list when the user clicks one.
 */
@SuppressWarnings("rawtypes")
public interface CompletionLayout {

    /**
     * Color scheme changed
     */
    void onApplyColorScheme(@NonNull EditorColorScheme colorScheme);

    /**
     * Attach the {@link EditorAutoCompletion}.
     * This is called first before other methods are called.
     */
    void setEditorCompletion(@NonNull EditorAutoCompletion completion);

    /**
     * Inflate the layout, return the view root.
     */
    @NonNull
    View inflate(@NonNull Context context);

    /**
     * Get the {@link AdapterView} to display completion items
     */
    @NonNull
    AdapterView getCompletionList();

    /**
     * Set loading state.
     * You may update your layout to show other contents
     */
    void setLoading(boolean loading);

    /**
     * Make the given position visible
     *
     * @param position        Item index
     * @param incrementPixels If you scroll the layout, this is a recommended value of each scroll. {@link EditorCompletionAdapter#getItemHeight()}
     */
    void ensureListPositionVisible(int position, int incrementPixels);

    /**
     * Some layout may support to display more animations,
     * this method provides control over the animation of the layout.
     */
    default void setEnabledAnimation(boolean enabledAnimation) {
        //ignore
    }
}
