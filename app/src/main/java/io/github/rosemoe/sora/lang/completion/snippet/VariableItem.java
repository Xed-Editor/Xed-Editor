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
package io.github.rosemoe.sora.lang.completion.snippet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class VariableItem extends SnippetItem implements PlaceHolderElement {

    private String name;
    private String defaultValue;
    private Transform transform;

    public VariableItem(int index, @NonNull String name, @Nullable String defaultValue) {
        this(index, name, defaultValue, null);
    }

    public VariableItem(int index, @NonNull String name, @Nullable String defaultValue, @Nullable Transform transform) {
        super(index);
        this.name = name;
        this.defaultValue = defaultValue;
        this.transform = transform;
    }

    public void setTransform(@Nullable Transform transform) {
        this.transform = transform;
    }

    @Nullable
    public Transform getTransform() {
        return transform;
    }

    public void setName(@NonNull String name) {
        this.name = name;
    }

    @NonNull
    public String getName() {
        return name;
    }

    public void setDefaultValue(@NonNull String defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Nullable
    public String getDefaultValue() {
        return defaultValue;
    }

    @NonNull
    @Override
    public VariableItem clone() {
        var n = new VariableItem(getStartIndex(), name, defaultValue);
        n.setIndex(getStartIndex(), getEndIndex());
        return n;
    }
}
