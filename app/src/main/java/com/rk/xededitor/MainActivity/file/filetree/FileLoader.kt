package com.rk.xededitor.MainActivity.file.filetree

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class FileLoader(private val loadedFiles: MutableMap<String, MutableList<File>> = mutableMapOf()) : Parcelable {
    private fun getFiles(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run { sortedWith(compareBy<File> { if (it.isFile) 1 else 0 }.thenBy { it.name.lowercase() }) }
    }
    
    fun getLoadedFiles(path: String) = loadedFiles[path] ?: emptyList()
    
    suspend fun loadFiles(path: String) = withContext(Dispatchers.IO) {
        loadedFiles[path] ?: run {
            val files = getFiles(File(path))
            loadedFiles[path] = files.toMutableList()
            files.forEach {
                if (it.isDirectory) {
                    loadedFiles[it.absolutePath] = getFiles(it).toMutableList()
                }
            }
            files.toMutableList()
        }
    }
    
    fun removeLoadedFile(currentFile: File): Boolean {
        if (currentFile.isDirectory) {
            loadedFiles.remove(currentFile.absolutePath)
        }
        val parent = currentFile.parentFile
        val parentPath = parent?.absolutePath
        val parentFiles = loadedFiles[parentPath]
        return parentFiles?.remove(currentFile) ?: false
    }
}