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
package io.github.rosemoe.sora.graphics;

import android.annotation.SuppressLint;
import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.rosemoe.sora.text.ContentLine;
import io.github.rosemoe.sora.text.FunctionCharacters;

public class Paint extends android.graphics.Paint {

    private float spaceWidth;
    private float tabWidth;
    private boolean renderFunctionCharacters;
    private SingleCharacterWidths widths;

    public Paint(boolean renderFunctionCharacters) {
        super();
        this.renderFunctionCharacters = renderFunctionCharacters;
        spaceWidth = measureText(" ");
        tabWidth = measureText("\t");
    }

    public void setRenderFunctionCharacters(boolean renderFunctionCharacters) {
        this.renderFunctionCharacters = renderFunctionCharacters;
        if (widths != null) {
            widths.clearCache();
        }
    }

    public boolean isRenderFunctionCharacters() {
        return renderFunctionCharacters;
    }

    private void ensureCacheObject() {
        if (widths == null) {
            widths = new SingleCharacterWidths(1);
        }
    }

    public void onAttributeUpdate() {
        spaceWidth = measureText(" ");
        tabWidth = measureText("\t");
        if (widths != null)
            widths.clearCache();
    }

    public float getSpaceWidth() {
        return spaceWidth;
    }

    public void setTypefaceWrapped(Typeface typeface) {
        super.setTypeface(typeface);
        onAttributeUpdate();
    }

    public void setTextSizeWrapped(float textSize) {
        super.setTextSize(textSize);
        onAttributeUpdate();
    }

    public void setFontFeatureSettingsWrapped(String settings) {
        super.setFontFeatureSettings(settings);
        onAttributeUpdate();
    }

    @Override
    public void setLetterSpacing(float letterSpacing) {
        super.setLetterSpacing(letterSpacing);
        onAttributeUpdate();
    }

    @SuppressLint("NewApi")
    public float myGetTextRunAdvances(@NonNull char[] chars, int index, int count, int contextIndex, int contextCount, boolean isRtl, @Nullable float[] advances, int advancesIndex, boolean fast) {
        if (fast) {
            ensureCacheObject();
            var width = 0f;
            for (int i = 0; i < count; i++) {
                char ch = chars[i + index];
                float charWidth;
                if (Character.isHighSurrogate(ch) && i + 1 < count && Character.isLowSurrogate(chars[index + i + 1])) {
                    charWidth = widths.measureCodePoint(Character.toCodePoint(ch, chars[index + i + 1]), this);
                    if (advances != null) {
                        advances[advancesIndex + i] = charWidth;
                        advances[advancesIndex + i + 1] = 0f;
                    }
                    i++;
                } else if (renderFunctionCharacters && FunctionCharacters.isEditorFunctionChar(ch)) {
                    charWidth = widths.measureText(FunctionCharacters.getNameForFunctionCharacter(ch), this);
                    if (advances != null) {
                        advances[advancesIndex + i] = charWidth;
                    }
                } else {
                    charWidth = (ch == '\t') ? tabWidth : widths.measureChar(ch, this);
                    if (advances != null) {
                        advances[advancesIndex + i] = charWidth;
                    }
                }
                width += charWidth;
            }
            return width;
        } else {
            float advance = getTextRunAdvances(chars, index, count, contextIndex, contextCount, isRtl, advances, advancesIndex);
            if (renderFunctionCharacters) {
                for (int i = 0;i < count;i++) {
                    char ch = chars[index + i];
                    if (FunctionCharacters.isEditorFunctionChar(ch)) {
                        float width = measureText(FunctionCharacters.getNameForFunctionCharacter(ch));
                        if (advances != null) {
                            advance -= advances[advancesIndex + i];
                            advances[advancesIndex + i] = width;
                        } else {
                            advance -= measureText(Character.toString(ch));
                        }
                        advance += width;
                    }
                }
            }
            return advance;
        }
    }

    /**
     * Get the advance of text with the context positions related to shaping the characters
     */
    public float measureTextRunAdvance(char[] text, int start, int end, int contextStart, int contextEnd, boolean fast) {
        return myGetTextRunAdvances(text, start, end - start, contextStart, contextEnd - contextStart, false, null, 0, fast);
    }

    /**
     * Find offset for a certain advance returned by {@link #measureTextRunAdvance(char[], int, int, int, int, boolean)}
     */
    public int findOffsetByRunAdvance(ContentLine text, int start, int end, float advance, boolean fast) {
        if (fast) {
            ensureCacheObject();
            var width = 0f;
            var textChars = text.getBackingCharArray();
            for (int i = start; i < end; i++) {
                char ch = textChars[i];
                float charWidth;
                int j = i;
                if (Character.isHighSurrogate(ch) && i + 1 < end && Character.isLowSurrogate(textChars[i + 1])) {
                    charWidth = widths.measureCodePoint(Character.toCodePoint(ch, textChars[i + 1]), this);
                    i++;
                } else if (renderFunctionCharacters && FunctionCharacters.isEditorFunctionChar(ch)) {
                    charWidth = widths.measureText(FunctionCharacters.getNameForFunctionCharacter(ch), this);
                } else {
                    charWidth = (ch == '\t') ? tabWidth : widths.measureChar(ch, this);
                }
                width += charWidth;
                if (width > advance) {
                    return Math.max(start, j - 1);
                }
            }
            return end;
        }
        if (renderFunctionCharacters) {
            int lastEnd = start;
            float current = 0f;
            var textChars = text.getBackingCharArray();
            for (int i = start;i < end;i++) {
                char ch = textChars[i];
                if (FunctionCharacters.isEditorFunctionChar(ch)) {
                    int result = lastEnd == i ? i : breakTextImpl(text, lastEnd, i, advance - current);
                    if (result < i) {
                        return result;
                    }
                    current += measureTextRunAdvance(textChars, lastEnd, i, lastEnd, i, false);
                    current += measureText(FunctionCharacters.getNameForFunctionCharacter(ch));
                    if (current >= advance) {
                        return i;
                    }
                    lastEnd = i + 1;
                }
            }
            if (lastEnd < end) {
                return breakTextImpl(text, lastEnd, end, advance - current);
            }
            return end;
        } else {
            return breakTextImpl(text, start, end, advance);
        }
    }

    private int breakTextImpl(ContentLine text, int start, int end, float advance) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getOffsetForAdvance(text.getBackingCharArray(), start, end, start, end, false, advance);
        } else {
            return start + breakText(text.getBackingCharArray(), start, end - start, advance, null);
        }
    }

}
