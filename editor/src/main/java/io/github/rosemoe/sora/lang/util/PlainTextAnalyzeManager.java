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
package io.github.rosemoe.sora.lang.util;

import androidx.annotation.NonNull;

import io.github.rosemoe.sora.lang.styling.Styles;
import io.github.rosemoe.sora.text.CharPosition;

/**
 * This class generate plain spans for content, in case when
 * using a language without analysis you can still provide context-free completions.
 * By default, editor does not show completions when no span is set
 * on the text. This would be helpful for enabling completion for pure texts.
 *
 * @author Rosemoe
 */
public final class PlainTextAnalyzeManager extends BaseAnalyzeManager {

    @Override
    public void insert(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence insertedContent) {

    }

    @Override
    public void delete(@NonNull CharPosition start, @NonNull CharPosition end, @NonNull CharSequence deletedContent) {

    }

    @Override
    public void rerun() {
        final var receiver = getReceiver();
        final var ref = getContentRef();
        if (receiver != null && ref != null) {
            var style = new Styles();
            style.spans = new PlainTextSpans(ref.getLineCount());
            receiver.setStyles(this, style);
        } else if (receiver != null) {
            receiver.setStyles(this, null);
        }
    }

}
