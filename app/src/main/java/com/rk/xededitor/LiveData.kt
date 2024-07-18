package com.rk.xededitor

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel

class LiveData : ViewModel() {
  private val openedFiles = ArrayList<DocumentFile>()
  private val pendingFiles = ArrayList<DocumentFile>()
  private var looping = false;
  
  fun setLooping(loop:Boolean){
    if (!loop){
      openedFiles.addAll(pendingFiles)
    }
    looping = loop
  }
  
  fun getOpenedFiles() : ArrayList<DocumentFile>{
    return openedFiles
  }
  fun addFile(file: DocumentFile){
    if (looping){
      pendingFiles.add(file)
    }else{
      openedFiles.add(file)
    }
  }
}