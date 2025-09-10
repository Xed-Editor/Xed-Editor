package com.rk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FileManager {
    private val gits = mutableSetOf<String>()
    suspend fun findGitRoot(file: File): File? {
        return withContext(Dispatchers.IO) {
            gits.forEach { root ->
                if (file.absolutePath.contains(root)) {
                    return@withContext File(root)
                }
            }
            var currentFile = file
            while (currentFile.parentFile != null) {
                if (File(currentFile.parentFile, ".git").exists()) {
                    currentFile.parentFile?.let { gits.add(it.absolutePath) }
                    return@withContext currentFile.parentFile
                }
                currentFile = currentFile.parentFile!!
            }
            return@withContext null
        }

    }
}