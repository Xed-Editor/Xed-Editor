package com.rk.editor

import android.content.Context
import com.rk.file.FileTypeManager
import io.github.rosemoe.sora.lsp.editor.text.MarkdownCodeHighlighterRegistry
import io.github.rosemoe.sora.lsp.editor.text.withEditorHighlighter

object CodeHighlighter {
    fun registerMarkdownCodeHighlighter(context: Context) {
        MarkdownCodeHighlighterRegistry.global.withEditorHighlighter { languageName ->
            val textmateScope =
                FileTypeManager.fromMarkdownName(languageName).textmateScope ?: return@withEditorHighlighter null

            val language = LanguageManager.createLanguageBlocking(context, textmateScope)
            val colorScheme = ThemeManager.createColorSchemeBlocking(context)
            language to colorScheme
        }
    }
}
