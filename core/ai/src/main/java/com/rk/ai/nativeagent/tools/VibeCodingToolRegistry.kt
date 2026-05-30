package com.rk.ai.nativeagent.tools

import com.rk.ai.models.Tool
import com.rk.ai.service.IdeService

class VibeCodingToolRegistry(private val ideService: IdeService) {

    private val fileTools by lazy { VibeCodingFileTools(ideService) }
    private val editorTools by lazy { VibeCodingEditorTools(ideService) }
    private val searchTools by lazy { VibeCodingSearchTools(ideService) }
    private val lspTools by lazy { VibeCodingLspTools(ideService) }
    private val gitTools by lazy { VibeCodingGitTools(ideService) }
    private val terminalTools by lazy { VibeCodingTerminalTools(ideService) }
    private val projectTools by lazy { VibeCodingProjectTools(ideService) }
    private val systemTools by lazy { VibeCodingSystemTools(ideService) }

    val allTools: List<Tool> by lazy {
        fileTools.all +
            editorTools.all +
            searchTools.all +
            lspTools.all +
            gitTools.all +
            terminalTools.all +
            projectTools.all +
            systemTools.all
    }
}
