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

public class InterpolatedShellItem extends SnippetItem {

    private String shellCode;

    public InterpolatedShellItem(@NonNull String shellCode, int index) {
        super(index);
        this.shellCode = shellCode;
    }

    @NonNull
    public String getShellCode() {
        return shellCode;
    }

    public void setShellCode(@NonNull String shellCode) {
        this.shellCode = shellCode;
    }

    @NonNull
    @Override
    public InterpolatedShellItem clone() {
        var n = new InterpolatedShellItem(shellCode, getStartIndex());
        n.setIndex(getStartIndex(), getEndIndex());
        return n;
    }

}
