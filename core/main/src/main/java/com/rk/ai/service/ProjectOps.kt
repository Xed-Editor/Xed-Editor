package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface ProjectOps {
    fun getPrimaryWorkspacePath(): String
    suspend fun searchCode(query: String, limit: Int = 100): JsonArray
    suspend fun findFiles(query: String, limit: Int = 100): JsonArray
    suspend fun getProjectStructure(path: String, maxDepth: Int, maxItems: Int): String
    suspend fun getProjectConfig(workspacePath: String): JsonObject
    suspend fun getSymbolUnderCursor(): JsonObject
}
