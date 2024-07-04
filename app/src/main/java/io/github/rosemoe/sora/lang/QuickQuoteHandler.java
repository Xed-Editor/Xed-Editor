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
package io.github.rosemoe.sora.lang;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.TextRange;

public interface QuickQuoteHandler {

    /**
     * Checks whether the given input matches the requirement to invoke this handler
     *
     * @param candidateCharacter The character going to be inserted. Length can be 1 or 2.
     * @param text               Current text in editor
     * @param cursor             The range of cursor
     * @param style              Current code styles
     * @return Whether this handler consumed the event
     */
    @NonNull
    HandleResult onHandleTyping(@NonNull String candidateCharacter, @NonNull Content text, @NonNull TextRange cursor, @Nullable Styles style);

    class HandleResult {

        public final static HandleResult NOT_CONSUMED = new HandleResult(false, null);

        private boolean consumed;

        private TextRange newCursorRange;

        public HandleResult(boolean consumed, TextRange newCursorRange) {
            this.consumed = consumed;
            this.newCursorRange = newCursorRange;
        }

        public boolean isConsumed() {
            return consumed;
        }

        public void setConsumed(boolean consumed) {
            this.consumed = consumed;
        }

        public TextRange getNewCursorRange() {
            return newCursorRange;
        }

        public void setNewCursorRange(TextRange newCursorRange) {
            this.newCursorRange = newCursorRange;
        }
    }

}
