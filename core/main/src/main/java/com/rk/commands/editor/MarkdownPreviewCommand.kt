package com.rk.commands.editor

import com.rk.commands.CommandProvider
import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.commands.EditorNonActionContext
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.tabs.markdown.MarkdownPreviewTab

/**
 * Opens a native, themed Markdown preview ([MarkdownPreviewTab]) for the current file in a new tab.
 *
 * Only shown for Markdown files. Saves first so the preview reflects unsaved edits. Reuses an
 * already-open preview for the same file instead of stacking duplicates.
 */
class MarkdownPreviewCommand : EditorCommand() {
    override val id: String = "editor.markdown_preview"

    override fun getLabel(): String = strings.markdown_preview.getString()

    override fun action(editorActionContext: EditorActionContext) {
        CommandProvider.SaveCommand.action(editorActionContext)
        val file = editorActionContext.editorTab.file
        val viewModel = commandContext.mainViewModel
        val existing = viewModel.tabs.indexOfFirst { it is MarkdownPreviewTab && it.file == file }
        if (existing != -1) {
            viewModel.tabManager.setCurrentTab(existing)
        } else {
            viewModel.tabManager.addTab(MarkdownPreviewTab(file), switchToTab = true, checkDuplicate = false)
        }
    }

    override fun isSupported(editorNonActionContext: EditorNonActionContext): Boolean {
        return MarkdownRunner.matcher(editorNonActionContext.editorTab.file)
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.eye)
}
