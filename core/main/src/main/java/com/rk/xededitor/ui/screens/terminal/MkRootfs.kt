package com.rk.xededitor.ui.screens.terminal

import android.content.Context
import android.util.Log
import com.rk.libcommons.isMainThread
import com.rk.App.Companion.getTempDir
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.terminal.getDefaultBindings
import com.rk.terminal.newSandbox
import com.rk.terminal.readStderr
import com.rk.terminal.readStdout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.lang.Runtime.getRuntime
import kotlin.invoke

enum class NEXT_STAGE{
    NONE,
    EXTRACTION
}

suspend fun CoroutineScope.getNextStage(context: Context): NEXT_STAGE{
    if (isMainThread()) {
        throw RuntimeException("IO operation on the main thread")
    }

    val sandboxFile = File(getTempDir(), "sandbox.tar.gz")
    val rootfsFiles = sandboxDir().listFiles()?.filter {
        it.absolutePath != sandboxHomeDir().absolutePath && it.absolutePath != sandboxDir().child(
            "tmp"
        ).absolutePath
    } ?: emptyList()


    return if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
        NEXT_STAGE.NONE
    } else {
        NEXT_STAGE.EXTRACTION
    }
}
