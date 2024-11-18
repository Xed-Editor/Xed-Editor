package com.rk.xededitor.MainActivity.file.filetree

import android.os.Parcelable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import java.io.File

@Parcelize
class FileLoader(private val loadedFiles: MutableMap<String, MutableList<File>> = mutableMapOf()) : Parcelable {
    
    private val TAG="FileLoader"
    
    private inline fun getSortedFiles(file: File): List<File> {
        return (file.listFiles() ?: emptyArray()).run { sortedWith(compareBy<File> { if (it.isFile) 1 else 0 }.thenBy { it.name.lowercase() }) }
    }
    
    fun getLoadedFiles(path: String) = loadedFiles[path] ?: getSortedFiles(File(path)).also { loadedFiles[path] = it.toMutableList() }
    
    suspend fun loadFiles(path: String,layer:Int = 0,maxLayers:Int = 2):List<File> = withContext(Dispatchers.IO) {
        return@withContext loadedFiles.getOrPut(path) {
            val files = getSortedFiles(File(path))
            
            val newLayer = layer+1
            if (newLayer < maxLayers){
                Log.v(TAG,"preloading layer $newLayer $path")
                files.filter { it.isDirectory }
                    .map { directory ->
                        launch {
                            loadFiles(directory.absolutePath,newLayer)
                        } }
            }else{
                Log.v(TAG,"Reached Max allowed Layers $maxLayers")
            }
            return@getOrPut files.toMutableList()
        }
    }
    
    fun removeLoadedFile(currentFile: File): Boolean {
        if (currentFile.isDirectory) {
            loadedFiles.remove(currentFile.absolutePath)
        }
        val parentFiles = loadedFiles[currentFile.parentFile?.absolutePath]
        return parentFiles?.remove(currentFile) ?: false
    }
    
    fun createLoadedFile(file: File) {
        if (file.parentFile!!.isDirectory.not()) {
            throw RuntimeException("tried to create a file in a file")
        }
        loadedFiles[file.parentFile!!.absolutePath]!!.add(file)
    }
    
    fun renameLoadedFile(oldFile: File, newFile: File) {
        removeLoadedFile(oldFile)
        createLoadedFile(newFile)
    }
}