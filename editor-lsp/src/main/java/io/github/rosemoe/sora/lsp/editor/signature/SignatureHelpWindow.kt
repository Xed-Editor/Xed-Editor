/*******************************************************************************
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
 ******************************************************************************/

package io.github.rosemoe.sora.lsp.editor.signature

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.MeasureSpec
import android.widget.TextView
import io.github.rosemoe.sora.event.ColorSchemeUpdateEvent
import io.github.rosemoe.sora.event.subscribeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.base.EditorPopupWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureInformation

open class SignatureHelpWindow(editor: CodeEditor) : EditorPopupWindow(
    editor,
    FEATURE_HIDE_WHEN_FAST_SCROLL or FEATURE_SCROLL_AS_CONTENT
) {

    private var signatureBackgroundColor = 0
    private var highlightParameter = 0
    private var defaultTextColor = 0
    private val rootView: View = LayoutInflater.from(editor.context)
        .inflate(io.github.rosemoe.sora.lsp.R.layout.signature_help_tooltip_window, null, false)

    private val maxWidth = (editor.width * 0.67).toInt()
    private val maxHeight = (editor.dpUnit * 235).toInt()

    private val text =
        rootView.findViewById<TextView>(io.github.rosemoe.sora.lsp.R.id.signature_help_tooltip_text)
    private val locationBuffer = IntArray(2)
    protected val eventManager = editor.createSubEventManager()

    private lateinit var signatureHelp: SignatureHelp


    init {
        super.setContentView(rootView)

        eventManager.subscribeEvent<ColorSchemeUpdateEvent> { _, _ ->
            applyColorScheme()
        }

        applyColorScheme()
    }

    fun isEnabled() = eventManager.isEnabled

    fun setEnabled(enabled: Boolean) {
        eventManager.isEnabled = enabled
        if (!enabled) {
            dismiss()
        }
    }

    open fun show(signatureHelp: SignatureHelp) {
        this.signatureHelp = signatureHelp
        renderSignatureHelp()
        updateWindowSizeAndLocation()
        show()
    }


    private fun updateWindowSizeAndLocation() {
        rootView.measure(
            MeasureSpec.makeMeasureSpec(maxWidth, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST)
        )

        val width = rootView.measuredWidth
        val height = rootView.measuredHeight

        setSize(width, height)

        updateWindowPosition()
    }

    protected open fun updateWindowPosition() {
        val selection = editor.cursor.left()
        val charX = editor.getCharOffsetX(selection.line, selection.column)
        val charY = editor.getCharOffsetY(
            selection.line,
            selection.column
        ) - editor.rowHeight - 10 * editor.dpUnit
        editor.getLocationInWindow(locationBuffer)
        val restAbove = charY + locationBuffer[1]
        val restBottom = editor.height - charY - editor.rowHeight
        val windowY = if (restAbove > restBottom) {
            charY - height
        } else {
            charY + editor.rowHeight * 1.5f
        }
        if (windowY < 0) {
            dismiss()
            return
        }
        val windowX = (charX - width / 2).coerceAtLeast(0f)
        setLocationAbsolutely(windowX.toInt(), windowY.toInt())
    }

    private fun renderSignatureHelp() {

        val activeSignatureIndex = signatureHelp.activeSignature
        val activeParameterIndex = signatureHelp.activeParameter
        val signatures = signatureHelp.signatures

        val renderStringBuilder = SpannableStringBuilder()

        if (activeSignatureIndex < 0 || activeParameterIndex < 0) {
            Log.d("SignatureHelpWindow", "activeSignature or activeParameter is negative")
            return
        }

        if (activeSignatureIndex >= signatures.size) {
            Log.d("SignatureHelpWindow", "activeSignature is out of range")
            return
        }

        // Get only the activated signature
        for (i in 0..activeSignatureIndex) {
            formatSignature(
                signatures[i],
                activeParameterIndex,
                renderStringBuilder,
                isCurrentSignature = i == activeSignatureIndex
            )
            if (i < activeSignatureIndex) {
                renderStringBuilder.append("\n")
            }
        }

        text.text = renderStringBuilder
    }

    private fun formatSignature(
        signature: SignatureInformation,
        activeParameterIndex: Int,
        renderStringBuilder: SpannableStringBuilder,
        isCurrentSignature: Boolean
    ) {
        val label = signature.label
        val parameters = signature.parameters
        val activeParameter = parameters.getOrNull(activeParameterIndex)

        val parameterStart = label.substring(0, label.indexOf('('))
        val currentIndex = 0.coerceAtLeast(renderStringBuilder.lastIndex);

        renderStringBuilder.append(
            parameterStart,
            ForegroundColorSpan(defaultTextColor), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        renderStringBuilder.append(
            "("
        )

        for (i in 0 until parameters.size) {
            val parameter = parameters[i]
            if (parameter == activeParameter && isCurrentSignature) {
                renderStringBuilder.append(
                    parameter.label.left,
                    ForegroundColorSpan(highlightParameter),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                renderStringBuilder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    renderStringBuilder.lastIndex - parameter.label.left.length,
                    renderStringBuilder.lastIndex,
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (i != parameters.size - 1) {
                    renderStringBuilder.append(
                        ", ", ForegroundColorSpan(highlightParameter),
                        SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                renderStringBuilder.append(
                    parameter.label.left,
                    ForegroundColorSpan(defaultTextColor),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
                )

            }

            if (i != parameters.size - 1 && (!isCurrentSignature || parameter != activeParameter)) {
                renderStringBuilder.append(", ")
            }

        }

        renderStringBuilder.append(")")

        if (isCurrentSignature) {
            renderStringBuilder.setSpan(
                StyleSpan(Typeface.BOLD),
                currentIndex,
                renderStringBuilder.lastIndex,
                SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

    }


    private fun applyColorScheme() {
        val colorScheme = editor.colorScheme
        text.typeface = editor.typefaceText
        defaultTextColor = colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_NORMAL)

        highlightParameter =
            colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER)

        signatureBackgroundColor =
            colorScheme.getColor(EditorColorScheme.SIGNATURE_BACKGROUND)

        val background = GradientDrawable()
        background.cornerRadius = editor.dpUnit * 8
        background.setColor(colorScheme.getColor(EditorColorScheme.SIGNATURE_BACKGROUND))
        rootView.background = background

        if (isShowing) {
            renderSignatureHelp()
        }
    }


}