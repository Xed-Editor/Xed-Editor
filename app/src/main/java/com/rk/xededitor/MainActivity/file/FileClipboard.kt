package com.rk.xededitor.MainActivity.file

import java.io.File

@Suppress("NOTHING_TO_INLINE")
object FileClipboard {
     var fileClipboard: File? = null
     var isPasted: Boolean = true

    inline fun setFile(file: File?) {
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

    inline fun getFile(): File? {
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
