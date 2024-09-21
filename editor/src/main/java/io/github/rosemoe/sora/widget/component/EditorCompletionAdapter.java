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
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;

import java.util.List;

import io.github.rosemoe.sora.lang.completion.CompletionItem;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

/**
 * A class to make custom adapter for auto-completion window
 *
 * @see EditorCompletionAdapter#getItemHeight()
 * @see EditorCompletionAdapter#getView(int, View, ViewGroup, boolean)
 */
public abstract class EditorCompletionAdapter extends BaseAdapter implements Adapter {

    private EditorAutoCompletion window;
    private List<CompletionItem> items;

    /**
     * Called by {@link EditorAutoCompletion} to attach some arguments
     */
    public void attachValues(@NonNull EditorAutoCompletion window, @NonNull List<CompletionItem> items) {
        this.window = window;
        this.items = items;
    }

    @Override
    public CompletionItem getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return getItem(position).hashCode();
    }

    @Override
    public int getCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return getView(position, convertView, parent, position == window.getCurrentPosition());
    }

    /**
     * Get color scheme in editor
     */
    @NonNull
    protected EditorColorScheme getColorScheme() {
        return window.getEditor().getColorScheme();
    }

    /**
     * Get theme color from current color scheme
     *
     * @param type Type of color. Refer to {@link EditorColorScheme}
     * @see EditorColorScheme#getColor(int)
     */
    protected int getThemeColor(int type) {
        return getColorScheme().getColor(type);
    }

    /**
     * Get context from editor
     */
    @NonNull
    protected Context getContext() {
        return window.getContext();
    }

    /**
     * Implementation of this class should provide exact height of its item
     * <p>
     * The value will be used to calculate the height of completion window
     */
    public abstract int getItemHeight();

    /**
     * @param isCurrentCursorPosition Is the {@param position} currently selected
     * @see BaseAdapter#getView(int, View, ViewGroup)
     */
    protected abstract View getView(int position, View convertView, ViewGroup parent, boolean isCurrentCursorPosition);

}
