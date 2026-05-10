package com.rk.exec

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext

object ShellUtils {
    data class Result(val exitCode: Int, val output: String, val error: String, val timedOut: Boolean)

    suspend fun run(vararg command: String, timeoutSeconds: Long? = null): Result =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(*command).start()

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

            val timedOut =
                if (timeoutSeconds != null) {
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
                timedOut = timedOut,
            )
        }

    suspend fun runUbuntu(workingDir: String? = null, vararg command: String, timeoutSeconds: Long? = null): Result =
        withContext(Dispatchers.IO) {
            val process = ubuntuProcess(workingDir = workingDir, command = command.toList())

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

            val timedOut =
                if (timeoutSeconds != null) {
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
                timedOut = timedOut,
            )
        }

    suspend fun runUbuntuStreaming(
        workingDir: String? = null,
        vararg command: String,
        timeoutSeconds: Long? = null,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Result =
        withContext(Dispatchers.IO) {
            val process = ubuntuProcess(workingDir = workingDir, command = command.toList())
            val output = StringBuilder()
            val error = StringBuilder()

            val outputThread = Thread {
                runCatching {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        output.appendLine(line)
                        onStdout(line)
                    }
                }
            }
            val errorThread = Thread {
                runCatching {
                    process.errorStream.bufferedReader().forEachLine { line ->
                        error.appendLine(line)
                        onStderr(line)
                    }
                }
            }

            outputThread.start()
            errorThread.start()

            var timedOut = false
            try {
                val startedAt = System.currentTimeMillis()
                while (process.isAlive) {
                    coroutineContext.ensureActive()
                    if (timeoutSeconds != null && System.currentTimeMillis() - startedAt > timeoutSeconds * 1000) {
                        timedOut = true
                        process.destroyForcibly()
                        break
                    }
                    Thread.sleep(100)
                }
                if (!timedOut) process.waitFor()
            } finally {
                if (process.isAlive) process.destroyForcibly()
            }

            outputThread.join(1000)
            errorThread.join(1000)

            Result(
                exitCode = if (timedOut) -1 else runCatching { process.exitValue() }.getOrDefault(-1),
                output = output.toString().trim(),
                error = error.toString().trim(),
                timedOut = timedOut,
            )
        }
}
