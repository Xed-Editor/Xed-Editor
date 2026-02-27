package com.rk.editor.intelligent

import android.view.KeyEvent
import com.rk.editor.Editor
import com.rk.file.BuiltinFileType
import com.rk.settings.Settings
import io.github.rosemoe.sora.event.EditorKeyEvent

object BulletContinuation : IntelligentFeature() {
    override val id: String = "md.bullet_continuation"

    override val supportedExtensions: List<String> = BuiltinFileType.MARKDOWN.extensions

    private val QUOTE_REGEX = Regex("^> ")
    private val LIST_WHITESPACE_REGEX = Regex("^\\s*([-+*]|[0-9]+[.)]) +(\\[[ x]] +)?")
    private val LIST_REGEX = Regex("^([-+*]|[0-9]+[.)])( +\\[[ x]])?\$")
    private val UL_LIST_REGEX = Regex("^((\\s*[-+*] +)(\\[[ x]] +)?)")
    private val OL_LIST_REGEX = Regex("^(\\s*)([0-9]+)([.)])( +)((\\[[ x]] +)?)")

    override fun handleKeyEvent(event: EditorKeyEvent, editor: Editor) {
        if (event.action != KeyEvent.ACTION_DOWN) return

        if (event.keyCode == KeyEvent.KEYCODE_ENTER && event.modifiers == 0) {
            onEnter(editor) { event.markAsConsumed() }
        } else if (event.keyCode == KeyEvent.KEYCODE_TAB && !event.isCtrlPressed && !event.isAltPressed) {
            onTab(editor, event.isShiftPressed) { event.markAsConsumed() }
        }
    }

    private fun onTab(editor: Editor, shiftPressed: Boolean, consumeEvent: () -> Unit) {
        if (editor.cursor.leftLine != editor.cursor.rightLine) return
        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        val listMatch = LIST_WHITESPACE_REGEX.find(line)
        if (listMatch != null && (lineToCursor.endsWith(listMatch.value) || editor.isTextSelected)) {
            if (!shiftPressed) {
                editor.indentLines(false)
            } else {
                editor.unindentSelection()
            }
            consumeEvent()
            return
        }
    }

    private fun onEnter(editor: Editor, consumeEvent: () -> Unit) {
        if (editor.isTextSelected) return
        val lineIndexBefore = editor.cursor.leftLine
        val columnIndexBefore = editor.cursor.leftColumn

        val line = editor.text.getLine(lineIndexBefore)
        val lineToCursor = line.take(columnIndexBefore)

        // Handle quotes
        val quoteMatch = QUOTE_REGEX.find(line)
        if (quoteMatch != null) {
            if (line.trim().toString() == ">") {
                // If empty quote -> remove it on enter
                editor.text.delete(lineIndexBefore, 0, lineIndexBefore, line.length)
            } else {
                // If quote with text -> add empty quote on enter
                editor.text.insert(lineIndexBefore, columnIndexBefore, "\n> ")
            }

            consumeEvent()
            return
        }

        // If list item is empty -> remove it on enter
        val liMatch = LIST_REGEX.matchEntire(line.trim())
        if (liMatch != null) {
            editor.text.delete(lineIndexBefore, 0, lineIndexBefore, line.length)

            consumeEvent()
            return
        }

        // If unordered list item with text -> add empty list item on enter
        val ulLiMatch = UL_LIST_REGEX.find(lineToCursor)
        if (ulLiMatch != null) {
            val listPrefix = ulLiMatch.groupValues[1] // e.g. "- [x] " or "* "
            val appendedListItem = '\n' + listPrefix.replace("[x]", "[ ]")
            editor.text.insert(lineIndexBefore, columnIndexBefore, appendedListItem)

            consumeEvent()
            return
        }

        // If numbered list item with text -> add empty numbered list item on enter
        val olLiMatch = OL_LIST_REGEX.find(lineToCursor)
        if (olLiMatch != null) {
            val leadingSpace = olLiMatch.groupValues[1]
            val previousMarker = olLiMatch.groupValues[2]
            val delimiter = olLiMatch.groupValues[3]
            var trailingSpace = olLiMatch.groupValues[4]
            val checkbox = olLiMatch.groupValues[5].replace("[x]", "[ ]")

            val marker = (previousMarker.toInt() + 1).toString()

            // Remove trailing spaces on digit length change (e.g. 9 to 10) to align text
            val markerDiff = previousMarker.length - marker.length
            val newTrailingSpaceLength = (trailingSpace.length + markerDiff).coerceAtLeast(1)
            trailingSpace = " ".repeat(newTrailingSpaceLength)

            val appendedListItem = '\n' + leadingSpace + marker + delimiter + trailingSpace + checkbox
            editor.text.insert(lineIndexBefore, columnIndexBefore, appendedListItem)

            consumeEvent()
            return
        }
    }

    override fun isEnabled(): Boolean {
        return Settings.bullet_continuation
    }
}
