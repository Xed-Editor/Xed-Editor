package com.rk.utils

import android.os.Environment
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helpers for reasoning about Android storage limitations that affect builds.
 *
 * Android's shared storage (/sdcard, i.e. Documents, Downloads, …) is exposed through a FUSE /
 * sdcardfs layer that is mounted `noexec` and silently ignores Unix permission bits. As a result
 * `chmod +x` appears to succeed but the file still cannot be executed, which breaks gradle, aapt2,
 * python venvs and any native npm module. Build tooling must run from an exec-capable filesystem
 * (the app's private/sandbox storage).
 */
object StorageUtils {

    /** True if [file] lives under the primary shared-storage root (/sdcard). Cheap, no IO. */
    fun isOnSharedStorage(file: File): Boolean {
        val sharedRoot = runCatching { Environment.getExternalStorageDirectory().canonicalPath }.getOrNull() ?: return false
        val target = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        return target == sharedRoot || target.startsWith("$sharedRoot/")
    }

    /**
     * Actually probes whether [dir] can execute files, by writing a tiny script, marking it
     * executable and checking the result. Returns false on noexec volumes such as /sdcard.
     *
     * This performs filesystem IO, so it runs on [Dispatchers.IO].
     */
    suspend fun isExecCapable(dir: File): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                    if (!dir.exists()) dir.mkdirs()
                    val probe = File(dir, ".xed_exec_probe_${System.nanoTime()}")
                    try {
                        probe.writeText("#!/bin/sh\nexit 0\n")
                        // setExecutable returns false when the underlying fs refuses the exec bit.
                        val marked = probe.setExecutable(true, false)
                        marked && probe.canExecute()
                    } finally {
                        probe.delete()
                    }
                }
                .getOrDefault(false)
        }
}
