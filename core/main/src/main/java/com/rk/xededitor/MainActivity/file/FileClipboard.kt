package com.rk.xededitor.MainActivity.file

import com.rk.file_wrapper.FileObject

@Suppress("NOTHING_TO_INLINE")
object FileClipboard {
     var fileClipboard: FileObject? = null
     var isPasted: Boolean = true
     var isCut = false

    inline fun setFile(file: FileObject?) {
        synchronized(this) {
            fileClipboard = file
            isPasted = false
        }
    }

    inline fun clear() {
        synchronized(this) {
            fileClipboard = null
            isPasted = true
        }
    }

    inline fun getFile(): FileObject? {
        synchronized(this) {
            val file = fileClipboard
            if (isPasted) {
                fileClipboard = null
            }
            isPasted = true
            return file
        }
    }

    inline fun isEmpty(): Boolean {
        synchronized(this) {
            return fileClipboard == null
        }
    }

    inline fun markAsPasted() {
        synchronized(this) { isPasted = true }
    }
}
