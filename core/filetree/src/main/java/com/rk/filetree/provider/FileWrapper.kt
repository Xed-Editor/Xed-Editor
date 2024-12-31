package com.rk.filetree.provider

import android.net.Uri
import androidx.core.net.toUri
import com.rk.filetree.interfaces.FileObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

@Suppress("OVERRIDE_BY_INLINE")
class FileWrapper(val file: File) : FileObject {
    override fun listFiles(): List<FileObject> {
        val list = file.listFiles()
        if (list.isNullOrEmpty()) {
            return emptyList()
        }
        return list.map { f -> FileWrapper(f) }
    }

    inline override fun isDirectory(): Boolean {
        return file.isDirectory
    }

    inline override fun isFile(): Boolean {
        return file.isFile
    }

    inline override fun getName(): String {
        return file.name
    }

    inline override fun getParentFile(): FileObject? {
        return file.parentFile?.let { FileWrapper(it) }
    }

    inline override fun exists():Boolean {
        return file.exists()
    }

    inline override fun createNewFile():Boolean {
        return file.createNewFile()
    }

    inline override fun mkdir():Boolean {
        return file.mkdir()
    }

    inline override fun mkdirs():Boolean {
       return file.mkdirs()
    }

    inline override fun writeText(text: String) {
        file.writeText(text)
    }

    inline override fun getInputStream(): InputStream {
        return FileInputStream(file)
    }

    override fun getOutPutStream(append: Boolean): OutputStream {
        return FileOutputStream(file,append)
    }

    inline override fun getAbsolutePath(): String {
        return file.absolutePath
    }

    inline override fun length(): Long {
        return file.length()
    }

    inline override fun delete(): Boolean {
        return file.delete()
    }

    inline override fun toUri(): Uri {
        return file.toUri()
    }

    override fun hashCode(): Int {
        return getAbsolutePath().hashCode()
    }

    override fun toString(): String {
        return getAbsolutePath()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is FileWrapper){return false}
        return other.getAbsolutePath() == getAbsolutePath()
    }
}
