package com.rk.ai.service

interface TerminalOps {
    suspend fun runCommand(command: String, timeoutSeconds: Long): CommandResult
    suspend fun getTerminalOutput(lines: Int?): String
}
