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
package io.github.rosemoe.sora.langs.textmate.registry.model;

import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import org.eclipse.tm4e.core.internal.theme.raw.RawThemeReader;
import org.eclipse.tm4e.core.registry.IThemeSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import io.github.rosemoe.sora.langs.textmate.utils.StringUtils;

public class ThemeModel {

    public static final ThemeModel EMPTY = new ThemeModel("EMPTY");
    private IThemeSource themeSource;

    private IRawTheme rawTheme;

    private Theme theme;

    private String name;

    private boolean isDark;

    public ThemeModel(IThemeSource themeSource) {
        this.themeSource = themeSource;
        this.name = StringUtils.getFileNameWithoutExtension(themeSource.getFilePath());
    }

    public ThemeModel(IThemeSource themeSource, String name) {
        this.themeSource = themeSource;
        this.name = name;
    }

    private ThemeModel(String name) {
        themeSource = null;
        rawTheme = null;
        this.name = name;
        theme = Theme.createFromRawTheme(null, null);
    }

    public void setDark(boolean dark) {
        isDark = dark;
    }

    public boolean isDark() {
        return isDark;
    }

    public void load() throws Exception {
        load(null);
    }

    public void load(List<String> colorMap) throws Exception {
        rawTheme = RawThemeReader.readTheme(themeSource);
        theme = Theme.createFromRawTheme(rawTheme, colorMap);
    }

    public boolean isLoaded() {
        return theme != null;
    }

    @Nullable
    public IThemeSource getThemeSource() {
        return themeSource;
    }

    @Nullable
    public IRawTheme getRawTheme() {
        return rawTheme;
    }

    public Theme getTheme() {
        return theme;
    }

    public String getName() {
        return name;
    }
}
