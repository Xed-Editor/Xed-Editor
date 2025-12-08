package com.rk.terminal

import android.content.Context
import com.rk.App.Companion.getTempDir
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.file.sandboxHomeDir
import com.rk.utils.isMainThread
import java.io.File
import kotlinx.coroutines.CoroutineScope

enum class NEXT_STAGE {
    NONE,
    EXTRACTION,
}

suspend fun CoroutineScope.getNextStage(context: Context): NEXT_STAGE {
    if (isMainThread()) {
        throw RuntimeException("IO operation on the main thread")
    }

    val sandboxFile = File(getTempDir(), "sandbox.tar")
    val rootfsFiles =
        sandboxDir().listFiles()?.filter {
            it.absolutePath != sandboxHomeDir().absolutePath &&
                it.absolutePath != sandboxDir().child("tmp").absolutePath
        } ?: emptyList()

    return if (sandboxFile.exists().not() || rootfsFiles.isEmpty().not()) {
        NEXT_STAGE.NONE
    } else {
        NEXT_STAGE.EXTRACTION
    }
}
