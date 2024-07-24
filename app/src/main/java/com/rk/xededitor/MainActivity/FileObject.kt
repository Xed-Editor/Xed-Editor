package com.rk.xededitor.MainActivity

import java.io.InputStream
import java.io.OutputStream

interface FileObject {
  fun isFile() : Boolean
  fun isDirectory() : Boolean
  fun newfile(fileName: String) : Boolean
  fun newfolder(fileName: String) : Boolean
  fun delete() : Boolean
  fun exists() : Boolean
  fun listFiles() : List<FileObject>?
  fun getInputStream() : InputStream?
  fun getOutputStream() : OutputStream?
  fun getRealObject() : Any?
  fun getName() : String
  fun equals(fileObject: FileObject) : Boolean
  fun rename(fileName: String) : Boolean
}