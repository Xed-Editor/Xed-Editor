package com.rk.utils

import java.io.IOException
import java.io.OutputStreamWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Extension to read all stdout as a single string */
suspend fun Process.readStdout(): String =
    withContext(Dispatchers.IO) {
        try {
            inputStream.bufferedReader().use { reader ->
                if (inputStream.available() <= 0) return@use ""
                reader.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (e.message?.contains("Stream closed") == true) "" else throw e
        }
    }

suspend fun Process.readStderr(): String =
    withContext(Dispatchers.IO) {
        try {
            errorStream.bufferedReader().use { reader ->
                if (errorStream.available() <= 0) return@use ""
                reader.readText()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            if (e.message?.contains("Stream closed") == true) "" else throw e
        }
    }

/** Extension to write to process stdin */
suspend fun Process.writeInput(input: String, flush: Boolean = true) =
    withContext(Dispatchers.IO) {
        OutputStreamWriter(outputStream).use { writer ->
            writer.write(input)
            if (flush) writer.flush()
        }
    }

/** Extension to wait for process to finish and return exit code */
suspend fun Process.awaitExit(): Int = withContext(Dispatchers.IO) { waitFor() }

/** Extension to destroy process safely */
fun Process.terminate() {
    if (isAlive) destroy()
}

/** Extension to check if process is alive */
fun Process.isRunning(): Boolean = isAlive
