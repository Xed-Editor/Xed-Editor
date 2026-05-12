package com.rk.ai.service

import java.io.File

interface FileOps {
    fun resolvePath(path: String): File?
    fun listFiles(directory: File, recursive: Boolean, maxFiles: Int): List<String>
    suspend fun getFileContent(filePath: String): String?
    suspend fun writeFile(file: File, content: String)
    fun refreshEditors(filePath: String? = null, force: Boolean = false)
    suspend fun createFile(filePath: String, content: String?): String
    suspend fun deleteFile(filePath: String): String
    suspend fun renameFile(sourcePath: String, destPath: String): String
}
