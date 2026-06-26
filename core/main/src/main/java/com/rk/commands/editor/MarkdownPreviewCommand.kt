package com.rk.commands.editor

import com.rk.DefaultScope
import com.rk.commands.CommandProvider
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.runners.web.markdown.MarkdownRunner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch

/**
 * Opens the GitHub-style rendered preview for the current Markdown file.
 *
 * Only shown when the active editor tab holds a Markdown file. Saves first so the preview reflects
 * unsaved edits, then launches the existing [MarkdownRunner] (zero-md / github-markdown-css).
 */
@OptIn(DelicateCoroutinesApi::class)
class MarkdownPreviewCommand : EditorCommand() {
    override val id: String = "editor.markdown_preview"

    override fun getLabel(): String = strings.markdown_preview.getString()

    override fun action(editorActionContext: EditorActionContext) {
        val editorTab = editorActionContext.editorTab
        val activity = editorActionContext.currentActivity
        CommandProvider.SaveCommand.action(editorActionContext)
        DefaultScope.launch { MarkdownRunner.run(activity, editorTab.file) }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        return MarkdownRunner.matcher(editorNonActionContext.editorTab.file)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.eye)
}
