package com.rk.libcommons.editor

import android.content.Context
import android.util.AttributeSet
import android.util.Pair
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.rk.libcommons.R
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.SymbolInputView.ButtonConsumer
import kotlin.math.max

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

/**
 * A simple symbol input view implementation for editor.
 *
 *
 *
 * First, add your symbols by [.addSymbols].
 * Then, bind a certain editor by [.bindEditor] so that it works
 *
 * @author Rosemoe
 */
class SymbolInputView : LinearLayout {
    @JvmField
    var textColor = 0
    private var editor: CodeEditor? = null


    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        setBackgroundColor(context.resources.getColor(R.color.defaultSymbolInputBackgroundColor))
        orientation = HORIZONTAL
        setTextColor(context.resources.getColor(R.color.defaultSymbolInputTextColor))
    }

    /**
     * Bind editor for the view
     */
    fun bindEditor(editor: CodeEditor?) {
        this.editor = editor
    }

    /**
     * @see .setTextColor
     */
    fun getTextColor(): Int {
        return textColor
    }

    /**
     * Set text color in the panel
     */
    fun setTextColor(color: Int) {
        for (i in 0 until childCount) {
            (getChildAt(i) as Button).setTextColor(color)
        }
        textColor = color
    }

    /**
     * Remove all added symbols
     */
    fun removeSymbols() {
        removeAllViews()
    }

    /**
     * Add symbols to the view.
     * @param display The texts displayed in button
     * @param insertText The actual text to be inserted to editor when the button is clicked
     */
    fun addSymbols(display: Array<String?>, insertText: Array<String>) {
        val count =
            max(display.size.toDouble(), insertText.size.toDouble()).toInt()

        for (i in 0 until count) {
            val btn = Button(context, null, android.R.attr.buttonStyleSmall)
            btn.text = display[i]
            btn.background =
                ContextCompat.getDrawable(context, R.drawable.extra_keys_btn_background)
            btn.setTextColor(textColor)
            btn.textSize = 17f

            addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            val finalI = i
            btn.setOnClickListener { view: View ->
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                if (editor == null || !editor!!.isEditable) {
                    return@setOnClickListener
                }
                if ("\t" == insertText[finalI]) {
                    if (editor!!.snippetController.isInSnippet()) {
                        editor!!.snippetController.shiftToNextTabStop()
                    } else {
                        editor!!.indentOrCommitTab()
                    }
                } else {
                    editor!!.insertText(insertText[finalI], 1)
                }
            }
        }
    }


    fun addSymbols(keys: Array<Pair<String, OnClickListener>>) {
        for (key in keys) {
            val btn = Button(context, null, android.R.attr.buttonStyleSmall)
            btn.text = key.first
            btn.background =
                ContextCompat.getDrawable(context, R.drawable.extra_keys_btn_background)
            btn.setTextColor(textColor)
            btn.textSize = 17f

            addView(btn, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))

            if (key.second != null) {
                btn.setOnClickListener(key.second)
            }
        }
    }


    fun forEachButton(consumer: ButtonConsumer) {
        for (i in 0 until childCount) {
            consumer.accept(getChildAt(i) as Button)
        }
    }

    interface ButtonConsumer {
        fun accept(btn: Button)
    }
}