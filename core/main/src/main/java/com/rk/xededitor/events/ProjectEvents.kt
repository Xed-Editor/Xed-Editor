package com.rk.xededitor.events

import com.rk.file.FileObject

object ProjectEvents {
    data class onProjectAdd(val file:FileObject)
    data class onProjectRemoved(val file: FileObject)
    data class onProjectSelected(val path:String)
    class onRestoreProjects()
    class onSaveProjects()
}