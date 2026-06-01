package com.rk.ai.service

import com.google.gson.JsonArray

interface LspOps {
    suspend fun getDiagnostics(filePath: String): JsonArray
    suspend fun findDefinitions(filePath: String, line: Int, column: Int): JsonArray
    suspend fun findReferences(filePath: String, line: Int, column: Int): JsonArray
    fun renameSymbol(filePath: String, line: Int, column: Int, newName: String)
    suspend fun formatDocument(filePath: String)
}
