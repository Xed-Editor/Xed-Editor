package com.rk.commands.editor

import android.app.AlertDialog
import android.view.KeyEvent
import com.rk.activities.main.MainActivity
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.editor.Editor
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import io.github.rosemoe.sora.text.TextRange
import java.util.regex.Pattern

data class SymbolItem(
    val name: String,
    val type: String,
    val line: Int,
    val column: Int,
)

class GoToSymbolCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.go_to_symbol"

    override fun getLabel(): String = "Go to Symbol"

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        val symbols = extractSymbols(editor)

        if (symbols.isEmpty()) {
            return
        }

        val activity = editorActionContext.currentActivity
        val names = symbols.map { "${it.type} ${it.name} (line ${it.line + 1})" }.toTypedArray()

        AlertDialog.Builder(activity)
            .setTitle("Go to Symbol")
            .setItems(names) { _, which ->
                val symbol = symbols[which]
                editor.setSelection(symbol.line, symbol.column)
                // Scroll to make the symbol visible
                editor.ensureSelectionVisible()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun extractSymbols(editor: Editor): List<SymbolItem> {
        val symbols = mutableListOf<SymbolItem>()
        val text = editor.text
        val lineCount = text.lineCount

        // Patterns for common language constructs
        val patterns =
            listOf(
                // Functions/methods: function name(, fun name(, def name(, fn name(
                SymbolPattern("Function", Pattern.compile("""(?:function|fun|def|fn|func|sub|proc|method)\s+(\w+)""")),
                // Classes: class Name, struct Name, interface Name
                SymbolPattern("Class", Pattern.compile("""(?:class|struct|interface|enum|trait|type|record)\s+(\w+)""")),
                // Variables: var name, val name, let name, const name
                SymbolPattern("Variable", Pattern.compile("""(?:var|val|let|const|final|static)\s+(\w+)""")),
                // Imports: import name
                SymbolPattern("Import", Pattern.compile("""(?:import|require|include|use|from)\s+([\w./*]+)""")),
                // Annotations: @name
                SymbolPattern("Annotation", Pattern.compile("""@(\w+)""")),
                // Labels/tags: # heading, ## heading
                SymbolPattern("Section", Pattern.compile("""^(#{1,6})\s+(.+)""")),
            )

        for (lineIndex in 0 until lineCount) {
            val line = text.getLine(lineIndex).toString()

            for (pattern in patterns) {
                val matcher = pattern.regex.matcher(line)
                while (matcher.find()) {
                    val name = matcher.group(1) ?: continue
                    val column = matcher.start()
                    symbols.add(SymbolItem(name, pattern.type, lineIndex, column))
                }
            }
        }

        return symbols.sortedWith(compareBy({ it.line }, { it.column }))
    }

    private data class SymbolPattern(val type: String, val regex: Pattern)

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.arrow_outward)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_T, ctrl = true, shift = true)
}
