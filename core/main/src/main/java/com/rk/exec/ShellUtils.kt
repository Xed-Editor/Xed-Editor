package com.rk.exec

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.util.concurrent.TimeUnit

object ShellUtils {

    data class Result(
        val exitCode: Int,
        val output: String,
        val error: String,
        val timedOut: Boolean
    )

    suspend fun run(
        vararg command: String,
        timeoutSeconds: Long? = null
    ): Result = withContext(Dispatchers.IO) {

        val process = ProcessBuilder(*command).start()

        val output = StringBuilder()
        val error = StringBuilder()

        val outputThread = Thread {
            process.inputStream.bufferedReader().forEachLine {
                output.appendLine(it)
            }
        }

        val errorThread = Thread {
            process.errorStream.bufferedReader().forEachLine {
                error.appendLine(it)
            }
        }

        outputThread.start()
        errorThread.start()

        val timedOut = if (timeoutSeconds != null) {
            !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        } else {
            process.waitFor()
            false
        }

        if (timedOut) {
            process.destroyForcibly()
        }

        outputThread.join()
        errorThread.join()

        Result(
            exitCode = if (timedOut) -1 else process.exitValue(),
            output = output.toString().trim(),
            error = error.toString().trim(),
            timedOut = timedOut
        )
    }
}