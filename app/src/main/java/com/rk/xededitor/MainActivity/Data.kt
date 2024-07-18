package com.rk.xededitor.MainActivity

import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel

class Data : ViewModel() {
  val openedFiles = ArrayList<DocumentFile>()
  val newFileBools = ArrayList<Boolean>()
}