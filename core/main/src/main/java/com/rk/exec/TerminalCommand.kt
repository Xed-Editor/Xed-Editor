package com.rk.exec

var pendingCommand: TerminalCommand? = null

data class TerminalCommand(
    val sandbox: Boolean = true,
    val exe: String,
    val args: List<String> = emptyList(),
    val id: String,
    val terminatePreviousSession: Boolean = true,
    val workingDir: String? = null,
    val env: List<String> = emptyList(),
)
