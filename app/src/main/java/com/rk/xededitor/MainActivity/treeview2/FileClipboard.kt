package com.rk.xededitor

import androidx.documentfile.provider.DocumentFile
import java.io.File

class FileClipboard private constructor() {
  companion object {
    @Volatile
    private var fileClipboard: File? = null
    
    @Volatile
    private var isPasted: Boolean = true
    
    @JvmStatic
    fun setFile(file: File?) {
      synchronized(this) {
        fileClipboard = file
        isPasted = false
      }
    }
    
    @JvmStatic
    fun clear() {
      synchronized(this) {
        fileClipboard = null
        isPasted = true
      }
    }
    
    @JvmStatic
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
    
    @JvmStatic
    fun isEmpty(): Boolean {
      synchronized(this) {
        return fileClipboard == null
      }
    }
    
    @JvmStatic
    fun markAsPasted() {
      synchronized(this) {
        isPasted = true
      }
    }
  }
}
