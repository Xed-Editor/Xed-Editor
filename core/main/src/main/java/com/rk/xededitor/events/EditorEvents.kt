package com.rk.xededitor.events

import com.rk.file.FileObject
import com.rk.libcommons.editor.KarbonEditor

object EditorEvents {
    data class onNewInstance(val editor: KarbonEditor)
    data class onFileLoaded(val file:FileObject,val editor: KarbonEditor)
    data class onFileSaved(val file:FileObject,val editor: KarbonEditor)
    data class onRefresh(val file: FileObject,val editor: KarbonEditor)
    data class onEditorUndo(val file: FileObject,val editor: KarbonEditor)
    data class onEditorRedo(val file: FileObject,val editor: KarbonEditor)
    data class onEditorRemoved(val file: FileObject)
}