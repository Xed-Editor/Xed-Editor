package com.rk.libtreeview.interfaces

interface onClickListener {
    fun onFileClick(fileObject: FileObject)
    fun onFileLongClick(fileObject: FileObject)
    fun onFolderClick(fileObject: FileObject)
    fun onFolderLongClick(fileObject: FileObject)
}