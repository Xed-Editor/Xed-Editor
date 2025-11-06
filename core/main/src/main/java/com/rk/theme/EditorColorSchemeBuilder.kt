package com.rk.theme

import androidx.core.graphics.toColorInt
import com.rk.utils.toast
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import java.lang.reflect.Modifier

// Represents a color mapping: EditorColorScheme.KEY → #AARRGGBB
data class EditorColor(val key: Int, val color: Int)

// Cache the mapping to avoid repeated reflection
private val EDITOR_COLOR_MAPPING: Map<String, Int> by lazy {
    getEditorColorSchemeMapping()
}

private fun String.toColorIntSafe(): Int = runCatching {
    toColorInt()
}.getOrElse { exception ->
    toast(exception.message)
    -1 // Invalid color marker
}

// Map JSON-like colors to EditorColorScheme keys
fun mapEditorColorScheme(rawScheme: Map<String, String>?): List<EditorColor> {
    if (rawScheme.isNullOrEmpty()) return emptyList()

    val result = mutableListOf<EditorColor>()

    rawScheme.forEach { (rawKey, hexColor) ->
        val normalizedKey = rawKey.lowercase().trim()
        val editorKey = EDITOR_COLOR_MAPPING[normalizedKey]

        val colorInt = hexColor.toColorIntSafe()
        if (editorKey != null && colorInt != -1) {
            result.add(EditorColor(editorKey, colorInt))
        }
    }

    return result
}

// Reflection: Extract all `public static final int` from EditorColorScheme
fun getEditorColorSchemeMapping(): Map<String, Int> {
    return EditorColorScheme::class.java.declaredFields
        .filter { field ->
            Modifier.isPublic(field.modifiers) &&
                    Modifier.isStatic(field.modifiers) &&
                    Modifier.isFinal(field.modifiers) &&
                    field.type == Int::class.javaPrimitiveType
        }
        .onEach { it.isAccessible = true }
        .associate { field ->
            field.name.lowercase() to (field.get(null) as Int)
        }
}