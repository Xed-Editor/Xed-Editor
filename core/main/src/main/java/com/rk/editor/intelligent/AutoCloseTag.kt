package com.rk.editor.intelligent

import com.rk.editor.Editor
import com.rk.file.BuiltinFileType
import com.rk.settings.Settings

object AutoCloseTag : IntelligentFeature() {
    override val id: String = "html.auto_close_tag"

    override val supportedExtensions: List<String> = BuiltinFileType.HTML.extensions + BuiltinFileType.HTMX.extensions

    override val triggerCharacters: List<Char> = listOf('>', '/')

    private val OPEN_TAG_REGEX = Regex("<([_a-zA-Z][a-zA-Z0-9:\\-_.]*)(?:\\s+[^<>]*?[^\\s/<>=]+?)*?\\s?(/|>)$")

    private val selfClosingTags =
        listOf(
            "area",
            "base",
            "br",
            "col",
            "command",
            "embed",
            "hr",
            "img",
            "input",
            "keygen",
            "link",
            "meta",
            "param",
            "source",
            "track",
            "wbr",
        )

    override fun handleInsertChar(triggerCharacter: Char, editor: Editor) {
        if (editor.cursor.isSelected) return
        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        val result = OPEN_TAG_REGEX.find(lineToCursor) ?: return
        val tagName = result.groupValues[1].lowercase()
        val endingChar = result.groupValues[2]

        val evenSingleQuotes = lineToCursor.count { it == '\'' } % 2 == 0
        val evenDoubleQuotes = lineToCursor.count { it == '\"' } % 2 == 0
        val evenBackticks = lineToCursor.count { it == '`' } % 2 == 0
        if (!evenSingleQuotes && !evenDoubleQuotes && !evenBackticks) return

        if (endingChar == ">") {
            if (selfClosingTags.contains(tagName)) return
            editor.text.insert(lineIndexBefore, columnIndexBefore, "</$tagName>")
            editor.setSelection(lineIndexBefore, columnIndexBefore)
        } else {
            if (lineToCursor.length < line.length) return
            if (lineToCursor[columnIndexBefore - 2] != ' ') {
                editor.text.insert(lineIndexBefore, columnIndexBefore - 1, " ")
            }
            editor.text.insert(editor.cursor.leftLine, editor.cursor.leftColumn, ">")
        }
    }

    override fun isEnabled(): Boolean {
        return Settings.auto_close_tags
    }
}
