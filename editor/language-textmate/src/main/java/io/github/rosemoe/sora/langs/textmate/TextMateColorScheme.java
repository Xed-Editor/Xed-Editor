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
package io.github.rosemoe.sora.langs.textmate;

import android.graphics.Color;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.tm4e.core.internal.theme.Theme;
import org.eclipse.tm4e.core.internal.theme.raw.IRawTheme;
import org.eclipse.tm4e.core.internal.theme.raw.RawTheme;
import org.eclipse.tm4e.core.registry.IThemeSource;

import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry;
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel;
import io.github.rosemoe.sora.widget.CodeEditor;
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme;

public class TextMateColorScheme extends EditorColorScheme implements ThemeRegistry.ThemeChangeListener {

    private Theme theme;

    private IRawTheme rawTheme;

    @Deprecated
    private IThemeSource themeSource;

    private ThemeModel currentTheme;

    private final ThemeRegistry themeRegistry;

    public TextMateColorScheme(ThemeRegistry themeRegistry, ThemeModel themeModel) throws Exception {
        this.themeRegistry = themeRegistry;

        currentTheme = themeModel;
    }

    @Deprecated
    public static TextMateColorScheme create(IThemeSource themeSource) throws Exception {
        return create(new ThemeModel(themeSource));
    }

    public static TextMateColorScheme create(ThemeModel themeModel) throws Exception {
        return create(ThemeRegistry.getInstance(), themeModel);
    }

    public static TextMateColorScheme create(ThemeRegistry themeRegistry) throws Exception {
        return create(ThemeRegistry.getInstance(), themeRegistry.getCurrentThemeModel());
    }

    public static TextMateColorScheme create(ThemeRegistry themeRegistry, ThemeModel themeModel) throws Exception {
        return new TextMateColorScheme(themeRegistry, themeModel);
    }


    public void setTheme(ThemeModel themeModel) {
        currentTheme = themeModel;
        super.colors.clear();
        this.rawTheme = themeModel.getRawTheme();
        this.theme = themeModel.getTheme();
        this.themeSource = themeModel.getThemeSource();
        applyDefault();
    }

    @Override
    public void onChangeTheme(ThemeModel newTheme) {
        setTheme(newTheme);
    }

    @Override
    public void applyDefault() {
        super.applyDefault();

        if (themeRegistry != null && !themeRegistry.hasListener(this)) {
            themeRegistry.addListener(this);
        }

        if (rawTheme == null) {
            return;
        }
        var settings = rawTheme.getSettings();

        RawTheme rawSubTheme;

        if (settings == null) {
            rawSubTheme = ((RawTheme) ((RawTheme) rawTheme).get("colors"));

            applyVSCTheme(rawSubTheme);
        } else {
            rawSubTheme = (RawTheme) ((List<?>) settings).get(0);
            rawSubTheme = (RawTheme) rawSubTheme.getSetting();

            applyTMTheme(rawSubTheme);

        }


    }


    private void applyVSCTheme(RawTheme RawTheme) {
        setColor(LINE_DIVIDER, Color.TRANSPARENT);

        String caret = (String) RawTheme.get("editorCursor.foreground");
        if (caret != null) {
            setColor(SELECTION_INSERT, Color.parseColor(caret));
        }


        String selection = (String) RawTheme.get("editor.selectionBackground");
        if (selection != null) {
            setColor(SELECTED_TEXT_BACKGROUND, Color.parseColor(selection));
        }

        String invisibles = (String) RawTheme.get("editorWhitespace.foreground");
        if (invisibles != null) {
            setColor(NON_PRINTABLE_CHAR, Color.parseColor(invisibles));
        }

        String lineHighlight = (String) RawTheme.get("editor.lineHighlightBackground");
        if (lineHighlight != null) {
            setColor(CURRENT_LINE, Color.parseColor(lineHighlight));
        }

        String background = (String) RawTheme.get("editor.background");
        if (background != null) {
            setColor(WHOLE_BACKGROUND, Color.parseColor(background));
            setColor(LINE_NUMBER_BACKGROUND, Color.parseColor(background));
        }

        String lineHighlightBackground = (String) RawTheme.get("editorLineNumber.foreground");

        if (lineHighlightBackground != null) {
            setColor(LINE_NUMBER, Color.parseColor(lineHighlightBackground));
        }

        String lineHighlightActiveForeground = (String) RawTheme.get("editorLineNumber.activeForeground");

        if (lineHighlightActiveForeground != null) {
            setColor(LINE_NUMBER_CURRENT, Color.parseColor(lineHighlightActiveForeground));
        }


        String foreground = (String) RawTheme.get("editor.foreground");

        if (foreground != null) {
            setColor(TEXT_NORMAL, Color.parseColor(foreground));
        }

        String completionWindowBackground = (String) RawTheme.get("completionWindowBackground");
        if (completionWindowBackground != null) {
            setColor(COMPLETION_WND_BACKGROUND, Color.parseColor(completionWindowBackground));
        }

        String completionWindowBackgroundCurrent = (String) RawTheme.get("completionWindowBackgroundCurrent");
        if (completionWindowBackgroundCurrent != null) {
            setColor(COMPLETION_WND_ITEM_CURRENT, Color.parseColor(completionWindowBackgroundCurrent));
        }

        String highlightedDelimetersForeground =
                (String) RawTheme.get("highlightedDelimetersForeground");
        if (highlightedDelimetersForeground != null) {
            setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, Color.parseColor(highlightedDelimetersForeground));
        }

        String tooltipBackground = (String) RawTheme.get("tooltipBackground");
        if (tooltipBackground != null) {
            setColor(DIAGNOSTIC_TOOLTIP_BACKGROUND, Color.parseColor(tooltipBackground));
        }

        String tooltipBriefMessageColor = (String) RawTheme.get("tooltipBriefMessageColor");
        if (tooltipBriefMessageColor != null) {
            setColor(DIAGNOSTIC_TOOLTIP_BRIEF_MSG, Color.parseColor(tooltipBriefMessageColor));
        }

        String tooltipDetailedMessageColor = (String) RawTheme.get("tooltipDetailedMessageColor");
        if (tooltipDetailedMessageColor != null) {
            setColor(DIAGNOSTIC_TOOLTIP_DETAILED_MSG, Color.parseColor(tooltipDetailedMessageColor));
        }

        String tooltipActionColor = (String) RawTheme.get("tooltipActionColor");
        if (tooltipActionColor != null) {
            setColor(DIAGNOSTIC_TOOLTIP_ACTION, Color.parseColor(tooltipActionColor));
        }


        String editorIndentGuideBackground = (String) RawTheme.get("editorIndentGuide.background");
        int blockLineColor = ((getColor(WHOLE_BACKGROUND) + getColor(TEXT_NORMAL)) / 2) & 0x00FFFFFF | 0x88000000;
        int blockLineColorCur = (blockLineColor) | 0xFF000000;

        if (editorIndentGuideBackground != null) {
            setColor(BLOCK_LINE, Color.parseColor(editorIndentGuideBackground));
        } else {
            setColor(BLOCK_LINE, blockLineColor);
        }

        String editorIndentGuideActiveBackground = (String) RawTheme.get("editorIndentGuide.activeBackground");

        if (editorIndentGuideActiveBackground != null) {
            setColor(BLOCK_LINE_CURRENT, Color.parseColor(editorIndentGuideActiveBackground));
        } else {
            setColor(BLOCK_LINE_CURRENT, blockLineColorCur);
        }

    }

    @Override
    public boolean isDark() {
        var superIsDark = super.isDark();
        if (superIsDark) {
            return true;
        }
        if (currentTheme != null) {
            return currentTheme.isDark();
        }
        return false;
    }

    private void applyTMTheme(RawTheme RawTheme) {
        setColor(LINE_DIVIDER, Color.TRANSPARENT);


        String caret = (String) RawTheme.get("caret");
        if (caret != null) {
            setColor(SELECTION_INSERT, Color.parseColor(caret));
        }


        String selection = (String) RawTheme.get("selection");
        if (selection != null) {
            setColor(SELECTED_TEXT_BACKGROUND, Color.parseColor(selection));
        }

        String invisibles = (String) RawTheme.get("invisibles");
        if (invisibles != null) {
            setColor(NON_PRINTABLE_CHAR, Color.parseColor(invisibles));
        }

        String lineHighlight = (String) RawTheme.get("lineHighlight");
        if (lineHighlight != null) {
            setColor(CURRENT_LINE, Color.parseColor(lineHighlight));
        }

        String background = (String) RawTheme.get("background");
        if (background != null) {
            setColor(WHOLE_BACKGROUND, Color.parseColor(background));
            setColor(LINE_NUMBER_BACKGROUND, Color.parseColor(background));
        }

        String foreground = (String) RawTheme.get("foreground");
        if (foreground != null) {
            setColor(TEXT_NORMAL, Color.parseColor(foreground));
        }

        String highlightedDelimetersForeground =
                (String) RawTheme.get("highlightedDelimetersForeground");
        if (highlightedDelimetersForeground != null) {
            setColor(HIGHLIGHTED_DELIMITERS_FOREGROUND, Color.parseColor(highlightedDelimetersForeground));
        }

        //TMTheme seems to have no fields to control BLOCK_LINE colors
        int blockLineColor = ((getColor(WHOLE_BACKGROUND) + getColor(TEXT_NORMAL)) / 2) & 0x00FFFFFF | 0x88000000;
        setColor(BLOCK_LINE, blockLineColor);
        int blockLineColorCur = (blockLineColor) | 0xFF000000;
        setColor(BLOCK_LINE_CURRENT, blockLineColorCur);
    }

    @Override
    public int getColor(int type) {
        if (type >= 255) {
            // Cache colors in super class
            var superColor = super.getColor(type);
            if (superColor == 0) {
                if (theme != null) {
                    String color = theme.getColor(type - 255);
                    var newColor = color != null ? Color.parseColor(color) : super.getColor(TEXT_NORMAL);
                    super.colors.put(type, newColor);
                    return newColor;
                }
                return super.getColor(TEXT_NORMAL);
            } else {
                return superColor;
            }
        }
        return super.getColor(type);
    }

    @Override
    public void detachEditor(@NonNull CodeEditor editor) {
        super.detachEditor(editor);
        themeRegistry.removeListener(this);
    }

    @Override
    public void attachEditor(@NonNull CodeEditor editor) {
        super.attachEditor(editor);
        try {
            themeRegistry.loadTheme(currentTheme);
        } catch (Exception e) {
            //throw new RuntimeException(e);
        }
        setTheme(currentTheme);
    }

    @Deprecated
    public IRawTheme getRawTheme() {
        return rawTheme;
    }


    @Deprecated
    public IThemeSource getThemeSource() {
        return themeSource;
    }
}
