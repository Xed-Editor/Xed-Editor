package com.rk.xededitor

import androidx.documentfile.provider.DocumentFile

class FileClipboard private constructor() {
  companion object {
    @Volatile
    private var fileClipboard: DocumentFile? = null
    
    @Volatile
    private var isPasted: Boolean = true
    
    @JvmStatic
    fun setFile(file: DocumentFile?) {
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
    fun getFile(): DocumentFile? {
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
