package com.rk.file
import android.os.Build
import android.os.FileObserver
import com.rk.isAppForeground
import com.rk.libcommons.toast
import java.io.File

class FileContentWatcher(fileObject: FileObject,onUpdate:()-> Unit){
    private var watcher: FileWatcher? = null
    init {
        if (fileObject is FileWrapper && fileObject.isFile()){
            watcher = FileWatcher(fileObject.file,onUpdate)
            watcher?.startWatching()
        }
    }

    fun stopWatching(){
        watcher?.stopWatching()
    }
}



private class FileWatcher(
    private val file: File,
    private val onUpdate: () -> Unit
) {

    private var observer: FileObserver? = null

    fun startWatching() {
        if (!file.exists() || !file.isFile) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            observer = object : FileObserver(file, MODIFY or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == MODIFY || event == CLOSE_WRITE) {
                        onUpdate()
                    }
                }
            }
        }else{
            observer = object : FileObserver(file.path, MODIFY or CLOSE_WRITE) {
                override fun onEvent(event: Int, path: String?) {
                    if (event == MODIFY || event == CLOSE_WRITE) {
                        onUpdate()
                    }
                }
            }
        }

        observer?.startWatching()
    }

    fun stopWatching() {
        observer?.stopWatching()
        observer = null
    }
}
