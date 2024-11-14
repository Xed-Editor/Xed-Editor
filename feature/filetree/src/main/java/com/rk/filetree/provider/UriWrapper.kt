package com.rk.filetree.provider

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.rk.filetree.interfaces.FileObject

class UriWrapper(
    private val context: Context,
    val uri: Uri,
    private val documentFile: DocumentFile = DocumentFile.fromTreeUri(context, uri) ?: throw IllegalArgumentException("Invalid Uri")
) : FileObject {
    
    override fun listFiles(): List<FileObject> {
        if (!isDirectory()) return emptyList()
        
        return documentFile.listFiles().mapNotNull { childDoc ->
            childDoc.uri.let { childUri ->
                UriWrapper(context, childUri, childDoc)
            }
        }
    }
    
    override fun isDirectory(): Boolean {
        return documentFile.isDirectory
    }
    
    override fun isFile(): Boolean {
        return documentFile.isFile
    }
    
    override fun getName(): String {
        return documentFile.name ?: uri.lastPathSegment ?: ""
    }
    
    override fun getParentFile(): UriWrapper? {
        val parentUri = try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parentDocId = DocumentsContract.getTreeDocumentId(uri)
            
            if (docId == parentDocId) return null
            
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId)
            DocumentFile.fromTreeUri(context, parentUri)?.uri
        } catch (e: Exception) {
            val parentPath = uri.path?.substringBeforeLast('/')
            if (parentPath != null) Uri.parse(parentPath) else null
        }
        
        return parentUri?.let { UriWrapper(context, it) }
    }
    
    override fun getStringRepresentation(): String {
        return uri.toString()
    }
    
    override fun createFromPath(path: String): FileObject {
        val newUri = Uri.parse(path)
        return UriWrapper(context, newUri)
    }
    
    override fun delete(): Boolean {
        return documentFile.delete()
    }
    
    override fun deleteRecursively(): Boolean {
        if (isDirectory()) {
            listFiles().forEach { it.deleteRecursively() }
        }
        return delete()
    }
    
    override fun moveTo(targetDir: FileObject): Boolean {
        if (targetDir !is UriWrapper) return false
        val newFile = targetDir.createNewFile(getName())
        return if (newFile != null && copyTo(newFile)) delete() else false
    }
    
    override fun copyTo(to: FileObject): Boolean {
        if (to !is UriWrapper) return false
        val targetFile = to.createNewFile(getName()) ?: return false
        context.contentResolver.openInputStream(uri)?.use { input ->
            context.contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                input.copyTo(output)
                return true
            }
        }
        return false
    }
    
    override fun rename(name: String): Boolean {
        return documentFile.renameTo(name)
    }
    
    override fun mkdirThis(): Boolean {
        return documentFile.createDirectory(getName()) != null
    }
    
    override fun createThisFile(): Boolean {
        return documentFile.createFile(getMimeType(), getName()) != null
    }
    
    override fun createNewFile(name: String): UriWrapper? {
        val newFile = documentFile.createFile("application/octet-stream", name)
        return newFile?.let { UriWrapper(context, it.uri, it) }
    }
    
    override fun createNewFolder(name: String): UriWrapper? {
        val newDir = documentFile.createDirectory(name)
        return newDir?.let { UriWrapper(context, it.uri, it) }
    }
    
    override fun exists(): Boolean {
        return documentFile.exists()
    }
    
    override fun getMimeType(): String {
        return documentFile.type ?: "application/octet-stream"
    }
    
    fun isRoot(): Boolean {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parentDocId = DocumentsContract.getTreeDocumentId(uri)
            docId == parentDocId
        } catch (e: Exception) {
            false
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UriWrapper) return false
        return getStringRepresentation() == other.getStringRepresentation()
    }
    
    override fun hashCode(): Int {
        return getStringRepresentation().hashCode()
    }
    
    companion object {
        fun fromUri(context: Context, uriString: String): UriWrapper? {
            return try {
                val uri = Uri.parse(uriString)
                UriWrapper(context, uri)
            } catch (e: Exception) {
                null
            }
        }
    }
}
