package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File

interface IdeService : FileOps, EditorOps, LspOps, ProjectOps, GitOps, TerminalOps,
    TerminalManagementOps, ClipboardOps, SettingsOps

data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int,
    val timedOut: Boolean
)
