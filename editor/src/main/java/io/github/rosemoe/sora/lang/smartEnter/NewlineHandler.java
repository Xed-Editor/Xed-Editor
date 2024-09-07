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
package io.github.rosemoe.sora.lang.smartEnter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;
import io.github.rosemoe.sora.text.Content;

/**
 * Perform text processing when user enters '\n' and selection size is 0
 */
public interface NewlineHandler {

    /**
     * Checks whether the given input matches the requirement to invoke this handler
     *
     * @param text     Current text in editor
     * @param position The position of cursor
     * @param style    Current code styles
     * @return Whether this handler should be called
     */
    boolean matchesRequirement(@NonNull Content text, @NonNull CharPosition position, @Nullable Styles style);

    /**
     * Handle newline and return processed content to insert
     *
     * @param text     Current text in editor
     * @param position The position of cursor
     * @param style    Current code styles
     * @return Actual content to insert
     */
    @NonNull
    NewlineHandleResult handleNewline(@NonNull Content text, @NonNull CharPosition position, @Nullable Styles style, int tabSize);

}
