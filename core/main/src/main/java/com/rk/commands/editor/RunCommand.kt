package com.rk.commands.editor

import android.view.KeyEvent
import com.rk.DefaultScope
import com.rk.commands.CommandProvider
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.commands.KeyCombination
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ProjectRunner
import com.rk.settings.Settings
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

/**
 * The editor "Run" (play) button. It is **project-aware**: visibility and behaviour are driven by
 * the type of the project the open file belongs to (see [ProjectRunner]).
 *
 *  - Shown for Python, Node, Rust, Go, static Web, and Fabric/Forge/Gradle projects.
 *  - Hidden for Android projects and for projects the editor can't identify.
 *
 * Clicking it runs (or builds) the project from its own folder in the terminal sandbox, or opens
 * the in-app HTML preview for static web projects.
 */
@OptIn(DelicateCoroutinesApi::class)
class RunCommand : EditorCommand() {
    override val id: String = "editor.run"

    override fun getLabel(): String = strings.run.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editorTab = editorActionContext.editorTab
        val activity = editorActionContext.currentActivity
        CommandProvider.SaveCommand.action(editorActionContext)
        DefaultScope.launch {
            Settings.runs += 1
            ProjectRunner.run(activity = activity, projectRoot = editorTab.projectRoot, file = editorTab.file)
        }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        val tab = editorNonActionContext.editorTab
        val rootPath = ProjectRunner.resolveProjectRootPath(tab.projectRoot, tab.file)
        return ProjectRunner.canRun(rootPath)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.run)

    override val defaultKeybinds: KeyCombination = KeyCombination(keyCode = KeyEvent.KEYCODE_F5)
}
