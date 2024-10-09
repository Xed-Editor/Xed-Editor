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
package io.github.rosemoe.sora.text.bidi;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Objects;

import io.github.rosemoe.sora.text.Content;
import io.github.rosemoe.sora.text.ContentListener;
import io.github.rosemoe.sora.util.IntPair;

public class ContentBidi implements ContentListener {

    public final static int MAX_BIDI_CACHE_ENTRY_COUNT = 64;

    private final DirectionsEntry[] entries = new DirectionsEntry[MAX_BIDI_CACHE_ENTRY_COUNT];
    private final Content text;
    private boolean enabled;

    public ContentBidi(@NonNull Content content) {
        text = Objects.requireNonNull(content);
        text.addContentListener(this);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            Arrays.fill(entries, null);
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    @NonNull
    public Directions getLineDirections(int line) {
        if (!enabled) {
            return new Directions(new long[]{IntPair.pack(0, 0)}, text.getLine(line).length());
        }
        synchronized (this) {
            for (int i = 0; i < entries.length; i++) {
                var entry = entries[i];
                if (entry != null && entry.line == line) {
                    return entry.dir;
                }
            }
        }
        var dir = TextBidi.getDirections(text.getLine(line));
        synchronized (this) {
            System.arraycopy(entries, 0, entries, 1, entries.length - 1);
            entries[0] = new DirectionsEntry(dir, line);
        }
        return dir;
    }

    @Override
    public synchronized void afterDelete(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence deletedContent) {
        var delta = endLine - startLine;
        for (int i = 0; i < entries.length; i++) {
            var entry = entries[i];
            if (entry == null) {
                continue;
            }
            if (entry.line >= startLine) {
                if (entry.line > endLine) {
                    entry.line -= delta;
                } else {
                    entries[i] = null;
                }
            }
        }
    }

    @Override
    public synchronized void afterInsert(@NonNull Content content, int startLine, int startColumn, int endLine, int endColumn, @NonNull CharSequence insertedContent) {
        var delta = endLine - startLine;
        for (int i = 0; i < entries.length; i++) {
            var entry = entries[i];
            if (entry == null) {
                continue;
            }
            if (entry.line > startLine) {
                entry.line += delta;
            } else if (entry.line == startLine) {
                entries[i] = null;
            }
        }
    }

    @Override
    public void beforeReplace(@NonNull Content content) {

    }

    public void destroy() {
        text.removeContentListener(this);
        Arrays.fill(entries, null);
    }

    private static class DirectionsEntry {

        Directions dir;

        int line;

        public DirectionsEntry(Directions dir, int line) {
            this.dir = dir;
            this.line = line;
        }
    }

}
