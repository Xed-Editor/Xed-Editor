package com.rk.ai.agent.tools

import android.content.Context
import com.rk.ai.models.Tool
import com.rk.ai.service.IdeService

class VibeCodingToolRegistry(
    private val ideService: IdeService,
    private val context: Context,
) {

    private val fileTools by lazy { VibeCodingFileTools(ideService) }
    private val editorTools by lazy { VibeCodingEditorTools(ideService) }
    private val searchTools by lazy { VibeCodingSearchTools(ideService) }
    private val lspTools by lazy { VibeCodingLspTools(ideService) }
    private val gitTools by lazy { VibeCodingGitTools(ideService) }
    private val terminalTools by lazy { VibeCodingTerminalTools(ideService) }
    private val projectTools by lazy { VibeCodingProjectTools(ideService) }
    private val systemTools by lazy { VibeCodingSystemTools(ideService, context) }
    private val diffTools by lazy { VibeCodingDiffTools(ideService) }
    private val webTools by lazy { VibeCodingWebTools(ideService) }
    private val githubTools by lazy { VibeCodingGitHubTools(ideService) }
    private val packageTools by lazy { VibeCodingPackageTools(ideService) }

    val allTools: List<Tool> by lazy {
        fileTools.all +
            editorTools.all +
            searchTools.all +
            lspTools.all +
            gitTools.all +
            terminalTools.all +
            projectTools.all +
            systemTools.all +
            diffTools.all +
            webTools.all +
            githubTools.all +
            packageTools.all
    }
}
