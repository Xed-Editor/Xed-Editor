package com.rk.xededitor.MainActivity.file.filetree

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class FileLoader(private val loadedFiles: MutableMap<String, MutableList<File>> = mutableMapOf()) : Parcelable {
    private inline fun getSortedFiles(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run { sortedWith(compareBy<File> { if (it.isFile) 1 else 0 }.thenBy { it.name.lowercase() }) }
    }
    
    fun getLoadedFiles(path: String) = loadedFiles[path] ?: emptyList()
    
    

suspend fun loadFiles(path: String, layer: Int = 0): List<File> = withContext(Dispatchers.IO) {
    loadedFiles.getOrPut(path) {
        val files = getSortedFiles(File(path)).toMutableList()

        // If there are deeper layers to load, preload them incrementally
        if (layer + 1 < 3) {
            files.filter { it.isDirectory }
                .forEach { directory ->
                    // Launch preloading for the next layer incrementally
                    launch {
                        loadFiles(directory.absolutePath, layer + 1)
                    }
                }
        }

        files
    }
}


    fun removeLoadedFile(currentFile: File): Boolean {
        if (currentFile.isDirectory) {
            loadedFiles.remove(currentFile.absolutePath)
        }
        val parentFiles = loadedFiles[currentFile.parentFile?.absolutePath]
        return parentFiles?.remove(currentFile) ?: false
    }
    
    fun createLoadedFile(file: File){
        if (file.parentFile!!.isDirectory.not()){
            throw RuntimeException("tried to create a file in a file")
        }
        loadedFiles[file.parentFile!!.absolutePath]!!.add(file)
    }
    
    fun renameLoadedFile(oldFile:File,newFile:File){
        removeLoadedFile(oldFile)
        createLoadedFile(newFile)
    }
}