package com.rk.commands.editor

import android.app.AlertDialog
import android.view.KeyEvent
import com.rk.commands.CommandContext
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.KeyCombination
import com.rk.editor.Editor
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.tabs.editor.EditorTab
import java.nio.charset.Charset

class ChangeEncodingCommand(commandContext: CommandContext) : EditorCommand(commandContext) {
    override val id: String = "editor.change_encoding"

    override fun getLabel(): String = "Change Encoding"

    override fun action(editorActionContext: EditorActionContext) {
        val editor = editorActionContext.editor
        val activity = editorActionContext.currentActivity

        val encodings =
            listOf(
                "UTF-8" to "utf-8",
                "UTF-16" to "utf-16",
                "UTF-16LE" to "utf-16le",
                "UTF-16BE" to "utf-16be",
                "ISO-8859-1" to "iso-8859-1",
                "US-ASCII" to "us-ascii",
                "windows-1252" to "windows-1252",
                "Shift_JIS" to "shift_jis",
                "EUC-JP" to "euc-jp",
                "ISO-2022-JP" to "iso-2022-jp",
            )

        // Find the current tab's encoding
        val viewModel = commandContext.mainViewModel
        val currentTab = viewModel.tabs.getOrNull(viewModel.currentTabIndex) as? EditorTab
        val currentEncoding = currentTab?.getCurrentEncoding() ?: Settings.encoding
        val names = encodings.map { it.first }.toTypedArray()
        val currentIndex = encodings.indexOfFirst { it.second.equals(currentEncoding, ignoreCase = true) }

        AlertDialog.Builder(activity)
            .setTitle("Select Encoding")
            .setSingleChoiceItems(names, currentIndex.coerceAtLeast(0)) { dialog, which ->
                val selected = encodings[which]
                // Change encoding for current tab only
                currentTab?.changeEncoding(selected.second)
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun getIcon(): Icon = Icon.DrawableRes(drawables.settings)

    override val defaultKeybinds: KeyCombination? = null
}
