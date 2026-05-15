package com.rk.ai.service

import com.google.gson.JsonArray

interface TerminalManagementOps {
    suspend fun listSessions(): JsonArray
    suspend fun createSession(name: String, workingDir: String): String
    suspend fun killSession(sessionId: String): String
    suspend fun writeToSession(sessionId: String, text: String): String
    suspend fun getSessionOutput(sessionId: String, lines: Int?): String
}
