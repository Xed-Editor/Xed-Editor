package com.rk.ai.mcp

import android.net.Uri
import java.io.File

interface FileManager {
    suspend fun saveUploadFromBytes(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): String

    fun getFile(id: String): File
}
