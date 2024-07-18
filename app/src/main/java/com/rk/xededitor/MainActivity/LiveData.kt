package com.rk.xededitor.MainActivity

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import com.rk.xededitor.MainActivity.treeview2.FileCacheMap
import java.util.LinkedList
import java.util.Queue

class LiveData : ViewModel() {
  private val openedFiles = LinkedList<DocumentFile>()
  private val pendingFiles = LinkedList<DocumentFile>()
  private val map = FileCacheMap<DocumentFile,Boolean>()
  
  private var looping = false
  
  fun setLooping(loop: Boolean) {
    if (!loop) {
      if (!openedFiles.containsAll(pendingFiles)) {
        openedFiles.addAll(pendingFiles)
      }
    }
    looping = loop
  }
  
  fun isLooping() : Boolean{
    return looping
  }
  
  fun getOpenedFiles(): LinkedList<DocumentFile> {
    return openedFiles
  }
  fun getNewFileMap() : FileCacheMap<DocumentFile,Boolean>{
    return map;
  }
  
  fun addFile(file: DocumentFile,isNewFile:Boolean) {
    if (looping) {
      pendingFiles.add(file)
    } else {
      openedFiles.add(file)
    }
  }
}