package com.rk.xededitor.MainActivity.file.filetree

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class FileLoader(private val loadedFiles: MutableMap<String, MutableList<File>> = mutableMapOf()) : Parcelable {
    private inline fun getSortedFiles(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run { sortedWith(compareBy<File> { if (it.isFile) 1 else 0 }.thenBy { it.name.lowercase() }) }
    }
    
    fun getLoadedFiles(path: String) = loadedFiles[path] ?: emptyList()
    
    suspend fun loadFiles(path: String,layer:Int = 0):List<File> = withContext(Dispatchers.IO) {
        return@withContext loadedFiles.getOrPut(path) {
            val files = getSortedFiles(File(path))
            val newLayer = layer+1
            if (newLayer < 3){
                files.filter { it.isDirectory }
                    .map { directory ->
                        async {
                            loadFiles(directory.absolutePath,newLayer)
                        } }
            }
            return@getOrPut files.toMutableList()
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