package com.rk.libcommons.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.text.InputType
import android.util.AttributeSet
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import com.rk.file_wrapper.FileObject
import com.rk.libcommons.isDarkMode
import com.rk.libcommons.isMainThread
import com.rk.libcommons.toastCatching
import com.rk.settings.Settings
import io.github.rosemoe.sora.text.ContentIO
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.component.DefaultCompletionLayout
import io.github.rosemoe.sora.widget.component.EditorAutoCompletion
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset


@Suppress("NOTHING_TO_INLINE")
class KarbonEditor : CodeEditor {
    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(
        context: Context,
        attrs: AttributeSet,
        defStyleAttr: Int,
    ) : super(context, attrs, defStyleAttr)


    init {
        applyFont()
        //getComponent(EditorAutoCompletion::class.java).isEnabled = true

        val darkTheme: Boolean = when (Settings.default_night_mode) {
            AppCompatDelegate.MODE_NIGHT_YES -> true
            AppCompatDelegate.MODE_NIGHT_NO -> false
            else -> isDarkMode(context)
        }

        val surface = if (darkTheme) {
            Color.BLACK
        } else {
            Color.WHITE
        }

        colorScheme.setColor(EditorColorScheme.WHOLE_BACKGROUND, surface)
        colorScheme.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, surface)
        colorScheme.setColor(EditorColorScheme.LINE_DIVIDER, surface)

        CoroutineScope(Dispatchers.Default).launch {
            applySettings()
        }

    }

    suspend fun applySettings() {
        getComponent(EditorAutoCompletion::class.java).setLayout(object :
            DefaultCompletionLayout() {
            override fun onApplyColorScheme(colorScheme: EditorColorScheme) {
                val typedValue = TypedValue()
                context.theme.resolveAttribute(
                    com.google.android.material.R.attr.colorSurface, typedValue, true
                )
                val colorSurface = typedValue.data
                (completionList.parent as? ViewGroup)?.background = ColorDrawable(colorSurface)
            }
        })

        withContext(Dispatchers.IO) {
            val tabSize = Settings.tab_size
            val pinLineNumber = Settings.pin_line_number
            val showLineNumber = Settings.show_line_numbers
            val cursorAnimation = Settings.cursor_animation
            val textSize = Settings.editor_text_size
            val wordWrap = Settings.wordwrap
            val keyboardSuggestion = Settings.show_suggestions
            val lineSpacing = Settings.line_spacing

            withContext(Dispatchers.Main) {
                props.deleteMultiSpaces = tabSize
                tabWidth = tabSize
                props.deleteEmptyLineFast = false
                props.useICULibToSelectWords = true
                setPinLineNumber(pinLineNumber)
                isLineNumberEnabled = showLineNumber
                isCursorAnimationEnabled = cursorAnimation
                setTextSize(textSize.toFloat())
                isWordwrap = wordWrap
                lineSpacingExtra = lineSpacing
                isDisableSoftKbdIfHardKbdAvailable = Settings.hide_soft_keyboard_if_hardware
                showSuggestions(keyboardSuggestion)
            }
        }
    }

    fun applyFont() {
        toastCatching {
            val fontPath = Settings.selected_font_path
            if (fontPath.isNotEmpty()) {
                val isAsset = Settings.is_selected_font_assest
                typefaceText = if (isAsset) {
                    Typeface.createFromAsset(context.assets, fontPath)
                } else {
                    Typeface.createFromFile(File(fontPath))
                }
            } else {
                typefaceText =
                    Typeface.createFromAsset(context.assets, "fonts/Default.ttf")
            }
        }?.let {
            toastCatching {
                typefaceText = Typeface.createFromAsset(context.assets, "fonts/Default.ttf")
            }
        }

    }

    suspend fun loadFile(fileObject: FileObject, encoding: Charset) {
        withContext(Dispatchers.IO) {
            toastCatching {
                withContext(Dispatchers.Main) {
                    setText(withContext(Dispatchers.IO) {
                        assert(isMainThread().not())
                        fileObject.getInputStream().use {
                            ContentIO.createFrom(it, encoding)
                        }
                    })
                }
            }
        }
    }


    fun showSuggestions(yes: Boolean) {
        inputType = if (yes) {
            InputType.TYPE_TEXT_VARIATION_NORMAL
        } else {
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
    }

    inline fun isShowSuggestion(): Boolean {
        return inputType != InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
    }

    private var isSearching: Boolean = false

    fun isSearching(): Boolean {
        return isSearching
    }

    fun setSearching(s: Boolean) {
        isSearching = s
    }
}
