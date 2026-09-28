package com.rk.editor

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodInfo
import android.view.inputmethod.InputMethodManager
import androidx.core.content.getSystemService
import com.rk.resources.R
import com.rk.utils.application

fun InputMethodInfo.languages(): Set<String> {
    return buildSet {
        repeat(subtypeCount) { index ->
            val subtype = getSubtypeAt(index)

            val languageTag = subtype.languageTag
            if (languageTag.isNotEmpty()) {
                add(languageTag)
            } else if (subtype.locale.isNotEmpty()) {
                add(subtype.locale)
            }
        }
    }
}

enum class EditorInputBehavior(val value: String, val stringRes: Int) {
    NO_SUGGESTIONS("no_suggestions", R.string.no_suggestions),
    SUGGESTIONS("suggestions", R.string.suggestions),
    SUGGESTIONS_AUTOCORRECT("suggestions_autocorrect", R.string.suggestions_autocorrect);

    companion object {
        fun fromValue(value: String): EditorInputBehavior {
            return entries.find { it.value == value } ?: SUGGESTIONS
        }

        fun getDefault(context: Context = application!!): EditorInputBehavior {
            val imm = context.getSystemService<InputMethodManager>() ?: return NO_SUGGESTIONS

            val currentIme =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    imm.currentInputMethodInfo
                } else null

            if (currentIme == null) {
                return NO_SUGGESTIONS
            }

            // See https://github.com/Xed-Editor/Xed-Editor/issues/1629
            val composingLanguages = setOf("zh", "ja", "ko")

            val usesComposition =
                currentIme.languages().any { language ->
                    language.substringBefore('-') in composingLanguages
                }

            return if (usesComposition) {
                SUGGESTIONS
            } else {
                NO_SUGGESTIONS
            }
        }
    }
}
