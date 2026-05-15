package com.rk.ai.service

interface ClipboardOps {
    suspend fun getClipboard(): String
    fun setClipboard(text: String)
}
