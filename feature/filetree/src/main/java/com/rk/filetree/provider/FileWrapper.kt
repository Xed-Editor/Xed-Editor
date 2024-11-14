package com.rk.filetree.provider

import com.rk.filetree.interfaces.FileObject
import java.io.File
import android.webkit.MimeTypeMap
import java.io.IOException
import java.io.Serializable

class FileWrapper(val file: File) : FileObject {
    private val isfile = file.isFile
    private val isDir = file.isDirectory
    
    override fun listFiles(): List<FileObject> {
        val list = file.listFiles()
        if (list.isNullOrEmpty()) {
            return emptyList()
        }
        return list.map { f -> FileWrapper(f) }
    }
    
    fun getNativeFile(): File {
        return file
    }
    
    override fun isDirectory(): Boolean {
        return isDir
    }
    
    override fun isFile(): Boolean {
        return isfile
    }
    
    override fun getName(): String {
        return file.name
    }
    
    override fun getParentFile(): FileObject? {
        return file.parentFile?.let { FileWrapper(it) }
    }
    
    fun getAbsolutePath(): String {
        return file.absolutePath
    }
    
    override fun getStringRepresentation(): String {
        return file.absolutePath
    }
    
    override fun createFromPath(path: String): FileObject {
        return FileWrapper(File(path))
    }
    
    override fun delete(): Boolean {
        return file.delete()
    }
    
    override fun deleteRecursively(): Boolean {
        return file.deleteRecursively()
    }
    
    override fun moveTo(targetDir: FileObject): Boolean {
        if (!exists() || !targetDir.exists() || !targetDir.isDirectory()) {
            return false
        }
        
        val destFile = File(targetDir.getStringRepresentation(), getName())
        return try {
            file.renameTo(destFile)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun copyTo(to: FileObject): Boolean {
        if (!exists() || !to.exists() || !to.isDirectory()) {
            return false
        }
        
        return try {
            if (isDirectory()) {
                // Create target directory
                val targetDir = File(to.getStringRepresentation(), getName())
                if (!targetDir.exists()) {
                    targetDir.mkdir()
                }
                
                // Copy all contents recursively
                file.listFiles()?.forEach { childFile ->
                    val childWrapper = FileWrapper(childFile)
                    childWrapper.copyTo(FileWrapper(targetDir))
                }
                true
            } else {
                // Copy single file
                file.copyTo(File(to.getStringRepresentation(), getName()))
                true
            }
        } catch (e: IOException) {
            false
        }
    }
    
    override fun rename(name: String): Boolean {
        if (!exists()) return false
        
        val newFile = File(file.parent, name)
        return try {
            file.renameTo(newFile)
        } catch (e: Exception) {
            false
        }
    }
    
    override fun mkdirThis(): Boolean {
        return file.mkdir()
    }
    
    override fun createThisFile(): Boolean {
        return try {
            file.createNewFile()
        } catch (e: IOException) {
            false
        }
    }
    
    override fun createNewFile(name: String): FileObject? {
        if (!isDirectory()) return null
        
        val newFile = File(file, name)
        newFile.createNewFile()
        return FileWrapper(newFile)
    }
    
    override fun createNewFolder(name: String): FileObject?{
        if (!isDirectory()) return null
        
        val newDir = File(file, name)
        newDir.mkdir()
        return FileWrapper(newDir)
    }
    
    override fun exists(): Boolean {
        return file.exists()
    }
    
    override fun getMimeType(): String {
        if (isDirectory()) return "directory"
        
        val extension = file.extension
        return if (extension.isEmpty()) {
            "application/octet-stream"
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase())
                ?: "application/octet-stream"
        }
    }
    
    class FileWrapperSerializable(val fileWrapper: FileWrapper) : Serializable{
    
    }
    override fun getSerializable(): Serializable {
        return FileWrapperSerializable(this)
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FileWrapper) return false
        return getStringRepresentation() == other.getStringRepresentation()
    }
    
    override fun hashCode(): Int {
        return getStringRepresentation().hashCode()
    }
}