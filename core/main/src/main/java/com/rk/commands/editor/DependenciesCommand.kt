package com.rk.commands.editor

import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings

/**
 * Opens the project [com.rk.projects.DependenciesDialog] for the current file's project, which
 * detects the project type and lets the user download required tools (JDKs, Node, Python, …).
 *
 * Shown whenever an editor tab is open. The dialog itself handles detection and installation.
 */
class DependenciesCommand : EditorCommand() {
    override val id: String = "editor.dependencies"

    override fun getLabel(): String = strings.dependencies.getString()

    override fun action(editorActionContext: EditorActionContext) {
        editorActionContext.editorTab.editorState.showDependenciesDialog = true
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean = true

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.download)
}
