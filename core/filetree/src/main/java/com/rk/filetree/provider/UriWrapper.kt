package com.rk.filetree.provider

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.rk.filetree.interfaces.FileObject

class UriWrapper(private val context: Context, private val uri: Uri, private val getPath:(Uri)->String) : FileObject {
    private val documentFile: DocumentFile? by lazy {
        DocumentFile.fromTreeUri(context, uri)
    }

    override fun listFiles(): List<FileObject> {
        val childFiles = documentFile?.listFiles() ?: emptyArray()
        return childFiles.map { UriWrapper(context, it.uri,getPath) }
    }

    override fun isDirectory(): Boolean {
        return documentFile?.isDirectory == true
    }

    override fun isFile(): Boolean {
        return documentFile?.isFile == true
    }

    override fun getName(): String {
        return documentFile?.name ?: "Unknown"
    }

    override fun getParentFile(): FileObject? {
        val parentUri = documentFile?.parentFile?.uri
        return parentUri?.let { UriWrapper(context, it,getPath) }
    }

    override fun getAbsolutePath(): String {
        return getPath.invoke(uri)
    }
}
