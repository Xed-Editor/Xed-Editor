package com.rk.xededitor.MainActivity.file.filetree

import android.os.Parcelable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File
import java.util.ArrayDeque

@Parcelize
class FileLoader(private val loadedFiles: MutableMap<String, List<File>> = mutableMapOf()) : Parcelable {
    private companion object {
        private val FILE_COMPARATOR = compareBy<File> { it.isFile }
            .thenBy { it.name.lowercase() }
    }
    
    private inline fun File.listFilesSorted(): List<File> =
        (listFiles()?.sortedWith(FILE_COMPARATOR) ?: emptyList())
    
    fun getLoadedFiles(path: String): List<File> = loadedFiles[path] ?: emptyList()
    
    suspend fun loadFiles(path: String): List<File> = withContext(Dispatchers.IO) {
        loadedFiles[path]?.let { return@withContext it }
        
        val rootDir = File(path)
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return@withContext emptyList()
        }
        
        val directoriesToProcess = ArrayDeque<File>().apply { add(rootDir) }
        val capacity = (directoriesToProcess.first().listFiles()?.size ?: 16) * 2
        val newLoadedFiles = HashMap<String, List<File>>(capacity)
        
        while (directoriesToProcess.isNotEmpty()) {
            val currentDir = directoriesToProcess.removeFirst()
            val files = currentDir.listFilesSorted()
            
            newLoadedFiles[currentDir.absolutePath] = files
            
            files.asSequence()
                .filter { it.isDirectory }
                .forEach { directoriesToProcess.add(it) }
        }
        
        loadedFiles.putAll(newLoadedFiles)
        newLoadedFiles[path] ?: emptyList()
    }
    
    fun removeLoadedFile(currentFile: File): Boolean {
        if (!currentFile.exists()) return false
        
        val parentPath = currentFile.parent ?: return false
        if (currentFile.isDirectory) {
            val directoriesToProcess = ArrayDeque<File>().apply { add(currentFile) }
            
            while (directoriesToProcess.isNotEmpty()) {
                val dir = directoriesToProcess.removeFirst()
                loadedFiles.remove(dir.absolutePath)?.let { files ->
                    files.asSequence()
                        .filter { it.isDirectory }
                        .forEach { directoriesToProcess.add(it) }
                }
            }
        }
        
        return loadedFiles[parentPath]?.let { parentFiles ->
            val updatedFiles = parentFiles.filterNot { it == currentFile }
            if (updatedFiles.size < parentFiles.size) {
                loadedFiles[parentPath] = updatedFiles
                true
            } else false
        } ?: false
    }
}