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
package com.rk.libcommons.editor;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.Pair;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.rk.xededitor.R;

import java.util.List;

import io.github.rosemoe.sora.widget.CodeEditor;

/**
 * A simple symbol input view implementation for editor.
 *
 * <p>
 * First, add your symbols by {@link #addSymbols(String[], String[])}.
 * Then, bind a certain editor by {@link #bindEditor(CodeEditor)} so that it works
 *
 * @author Rosemoe
 */
public class SymbolInputView extends LinearLayout {

    private int textColor;
    private CodeEditor editor;


    public SymbolInputView(Context context) {
        super(context);
        init();
    }

    public SymbolInputView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SymbolInputView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public SymbolInputView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setBackgroundColor(getContext().getResources().getColor(R.color.defaultSymbolInputBackgroundColor));
        setOrientation(HORIZONTAL);
        setTextColor(getContext().getResources().getColor(R.color.defaultSymbolInputTextColor));
    }

    /**
     * Bind editor for the view
     */
    public void bindEditor(CodeEditor editor) {
        this.editor = editor;
    }

    /**
     * @see #setTextColor(int)
     */
    public int getTextColor() {
        return textColor;
    }

    /**
     * Set text color in the panel
     */
    public void setTextColor(int color) {
        for (int i = 0; i < getChildCount(); i++) {
            ((Button) getChildAt(i)).setTextColor(color);
        }
        textColor = color;
    }

    /**
     * Remove all added symbols
     */
    public void removeSymbols() {
        removeAllViews();
    }

    /**
     * Add symbols to the view.
     *
     * @param display    The texts displayed in button
     * @param insertText The actual text to be inserted to editor when the button is clicked
     */
    public void addSymbols(@NonNull String[] display, @NonNull final String[] insertText) {
        int count = Math.max(display.length, insertText.length);

        for (int i = 0; i < count; i++) {
            var btn = new Button(getContext(), null, android.R.attr.buttonStyleSmall);
            btn.setText(display[i]);
            btn.setBackground(ContextCompat.getDrawable(getContext(), com.rk.resources.R.drawable.extra_keys_btn_background));
            btn.setTextColor(textColor);
            btn.setTextSize(17);

            addView(btn, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            int finalI = i;
            btn.setOnClickListener((view) -> {
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                if (editor == null || !editor.isEditable()) {
                    return;
                }
                if ("\t".equals(insertText[finalI])) {
                    if (editor.getSnippetController().isInSnippet()) {
                        editor.getSnippetController().shiftToNextTabStop();
                    } else {
                        editor.indentOrCommitTab();
                    }
                } else {
                    editor.insertText(insertText[finalI], 1);
                }
            });
        }
    }


    public void addSymbols(List<Pair<Drawable, OnClickListener>> keys) {
        for (Pair<Drawable, OnClickListener> key : keys) {
            var btn = new ImageButton(getContext(), null, android.R.attr.buttonStyleSmall);
            btn.setAdjustViewBounds(true);
            btn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            btn.setMaxHeight(70);
            btn.setMaxWidth(70);
            btn.setImageDrawable(key.first);
            btn.setBackground(ContextCompat.getDrawable(getContext(), com.rk.resources.R.drawable.extra_keys_btn_background));
            addView(btn, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            if (key.second != null) {
                btn.setOnClickListener(key.second);
            }
        }
    }

    public void addSymbols(@NonNull Pair<String, OnClickListener>[] keys) {
        for (Pair<String, OnClickListener> key : keys) {
            var btn = new Button(getContext(), null, android.R.attr.buttonStyleSmall);
            btn.setText(key.first);
            btn.setBackground(ContextCompat.getDrawable(getContext(), com.rk.resources.R.drawable.extra_keys_btn_background));
            btn.setTextColor(textColor);
            btn.setTextSize(17);

            addView(btn, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

            if (key.second != null) {
                btn.setOnClickListener(key.second);
            }


        }
    }


    public void forEachButton(@NonNull ButtonConsumer consumer) {
        for (int i = 0; i < getChildCount(); i++) {
            consumer.accept((Button) getChildAt(i));
        }
    }

    public interface ButtonConsumer {

        void accept(@NonNull Button btn);

    }

}
