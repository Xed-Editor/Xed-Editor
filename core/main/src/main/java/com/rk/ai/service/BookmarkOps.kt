package com.rk.ai.service

import com.google.gson.JsonArray

interface BookmarkOps {
    suspend fun toggleBookmark(filePath: String, line: Int): String
    suspend fun listBookmarks(): JsonArray
}
