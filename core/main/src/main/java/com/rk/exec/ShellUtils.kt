package com.rk.exec

import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ShellUtils {
    data class Result(val exitCode: Int, val output: String, val error: String, val timedOut: Boolean)

    private const val BUFFER_SIZE = 8192

    suspend fun run(vararg command: String, timeoutSeconds: Long? = null): Result =
        withContext(Dispatchers.IO) {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(false)
                .start()
            executeAndRead(process, timeoutSeconds)
        }

    suspend fun runUbuntu(
        workingDir: String? = null,
        vararg command: String,
        extraEnv: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
    ): Result =
        withContext(Dispatchers.IO) {
            val process = ubuntuProcess(workingDir = workingDir, command = command.toList(), extraEnv = extraEnv)
            executeAndRead(process, timeoutSeconds)
        }

    suspend fun runUbuntuStreaming(
        workingDir: String? = null,
        vararg command: String,
        extraEnv: Map<String, String> = emptyMap(),
        timeoutSeconds: Long? = null,
        onStdout: (String) -> Unit = {},
        onStderr: (String) -> Unit = {},
    ): Result =
        withContext(Dispatchers.IO) {
            val process = ubuntuProcess(workingDir = workingDir, command = command.toList(), extraEnv = extraEnv)
            val output = StringBuilder(1024)
            val error = StringBuilder(512)

            val outputThread = Thread(null, {
                runCatching {
                    BufferedReader(InputStreamReader(process.inputStream), BUFFER_SIZE).use { reader ->
                        reader.forEachLine { line ->
                            output.appendLine(line)
                            onStdout(line)
                        }
                    }
                }
            }, "shell-stdout").apply { isDaemon = true }
            val errorThread = Thread(null, {
                runCatching {
                    BufferedReader(InputStreamReader(process.errorStream), BUFFER_SIZE).use { reader ->
                        reader.forEachLine { line ->
                            error.appendLine(line)
                            onStderr(line)
                        }
                    }
                }
            }, "shell-stderr").apply { isDaemon = true }

            outputThread.start()
            errorThread.start()

            val timedOut =
                if (timeoutSeconds != null) {
                    !process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
                } else {
                    process.waitFor()
                    false
                }

            if (timedOut) process.destroyForcibly()

            outputThread.join(2000)
            errorThread.join(2000)

            Result(
                exitCode = if (timedOut) -1 else runCatching { process.exitValue() }.getOrDefault(-1),
                output = output.trimEnd().toString(),
                error = error.trimEnd().toString(),
                timedOut = timedOut,
            )
        }

    private suspend fun executeAndRead(process: Process, timeoutSeconds: Long?): Result =
        withContext(Dispatchers.IO) {
            val stdout = StringBuilder(1024)
            val stderr = StringBuilder(512)

            val outputThread = Thread {
                readStream(process.inputStream, stdout)
            }
            val errorThread = Thread {
                readStream(process.errorStream, stderr)
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

            if (timedOut) process.destroyForcibly()

            outputThread.join(2000)
            errorThread.join(2000)

            Result(
                exitCode = if (timedOut) -1 else process.exitValue(),
                output = stdout.trimEnd().toString(),
                error = stderr.trimEnd().toString(),
                timedOut = timedOut,
            )
        }

    private fun readStream(stream: java.io.InputStream, sb: StringBuilder) {
        val buf = CharArray(BUFFER_SIZE)
        BufferedReader(InputStreamReader(stream), BUFFER_SIZE).use { reader ->
            var read: Int
            while (reader.read(buf).also { read = it } != -1) {
                sb.append(buf, 0, read)
            }
        }
    }
}
