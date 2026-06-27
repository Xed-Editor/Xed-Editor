package com.rk.commands.editor

import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.ToggleableCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings

/**
 * Toggles whether the editor shows **all** open files together (on) or only the files that belong
 * to the project/directory currently selected in the drawer (off, the default).
 *
 * Off behaves like an IDE: each project shows only its own open files, so files from different
 * directories don't get mixed up. On restores the classic "everything open at once" behaviour.
 *
 * Lives in the editor toolbar (and its top-right overflow menu) as a toggle.
 */
class ToggleShowAllFilesCommand : EditorCommand(), ToggleableCommand {
    override val id: String = "editor.show_all_files"

    override fun getLabel(): String = strings.show_all_files.getString()

    override fun action(editorActionContext: EditorActionContext) {
        Settings.show_all_files = !Settings.show_all_files
    }

    override fun isOn(): Boolean = Settings.show_all_files

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.filter)
}
