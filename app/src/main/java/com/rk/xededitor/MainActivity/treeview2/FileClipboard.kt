package com.rk.xededitor

import java.io.File

object FileClipboard {
    private var fileClipboard: File? = null
    private var isPasted: Boolean = true

    fun setFile(file: File?) {
        synchronized(this) {
            fileClipboard = file
            isPasted = false
        }
    }

    fun clear() {
        synchronized(this) {
            fileClipboard = null
            isPasted = true
        }
    }

    fun getFile(): File? {
        synchronized(this) {
            val file = fileClipboard
            if (isPasted) {
                fileClipboard = null
            }
            isPasted = true
            return file
        }
    }

    fun isEmpty(): Boolean {
        synchronized(this) {
            return fileClipboard == null
        }
    }
    fun markAsPasted() {
        synchronized(this) {
            isPasted = true
        }
    }
}

