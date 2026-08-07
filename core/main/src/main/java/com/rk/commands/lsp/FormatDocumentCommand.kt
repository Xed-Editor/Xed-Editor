package com.rk.commands.lsp

import com.rk.commands.EditorActionContext
import com.rk.commands.EditorCommand
import com.rk.icons.Icon
import com.rk.resources.drawables
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.tabs.editor.EditorTab

class FormatDocumentCommand : EditorCommand() {
    override val id: String = "editor.format_document"

    override fun getLabel(): String = strings.format_document.getString()

    override fun action(context: EditorActionContext) {
        context.editorTab.registerTask(EditorTab.FORMAT_DOCUMENT_TASK_ID)
        context.editor.formatCodeAsync()
    }

    override fun getIcon(): Icon = Icon.ResourceIcon(drawables.auto_fix)
}
