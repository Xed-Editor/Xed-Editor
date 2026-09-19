package com.rk.exec

import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

object ShellUtils {
    data class Result(val exitCode: Int, val output: String, val error: String, val timedOut: Boolean)

    suspend fun run(
        vararg command: String,
        timeoutSeconds: Long? = null,
        workingDir: String? = null,
    ): Result =
        withContext(Dispatchers.IO) {
            val builder = ProcessBuilder(*command)
            if (workingDir != null) {
                builder.directory(File(workingDir))
            }
            await(builder.start(), timeoutSeconds)
        }

    suspend fun runUbuntu(workingDir: String? = null, vararg command: String, timeoutSeconds: Long? = null): Result =
        withContext(Dispatchers.IO) {
            await(ubuntuProcess(workingDir = workingDir, command = command.toList()), timeoutSeconds)
        }

    /**
     * Drains a started [process]'s streams and waits for it to exit.
     *
     * When the calling coroutine is cancelled — the chat's Stop button — the process is terminated
     * instead of being left running in the background. For [runUbuntu] that process is proot, which
     * runs with `--kill-on-exit`, so terminating it also tears down the sandboxed command.
     */
    private suspend fun await(process: Process, timeoutSeconds: Long?): Result {
        val output = StringBuilder()
        val error = StringBuilder()

        val outputThread = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { output.appendLine(it) } }
        }
        val errorThread = Thread {
            runCatching { process.errorStream.bufferedReader().forEachLine { error.appendLine(it) } }
        }

        outputThread.start()
        errorThread.start()

        var finished = false
        try {
            finished =
                suspendCancellableCoroutine { continuation ->
                    // Cancelling unblocks waitFor below by killing the process, so the coroutine
                    // does not sit here until the command finishes on its own.
                    continuation.invokeOnCancellation { process.terminate() }

                    val exited =
                        if (timeoutSeconds != null) {
                            process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                        } else {
                            process.waitFor()
                            true
                        }
                    continuation.resumeWith(kotlin.Result.success(exited))
                }
        } finally {
            if (!finished) {
                process.destroyForcibly()
                process.waitFor()
            }
            outputThread.join()
            errorThread.join()
        }

        // A cancelled run must surface as cancellation, not as a killed process with exit=-1.
        currentCoroutineContext().ensureActive()

        val timedOut = timeoutSeconds != null && !finished
        return Result(
            exitCode = if (timedOut) -1 else process.exitValue(),
            output = output.toString().trim(),
            error = error.toString().trim(),
            timedOut = timedOut,
        )
    }
}
