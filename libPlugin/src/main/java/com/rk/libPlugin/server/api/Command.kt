package com.rk.libPlugin.server.api

import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class Command(command: String) {
    private lateinit var process: Process;
    private lateinit var stdinWriter:OutputStreamWriter
    private lateinit var stdoutReader:BufferedReader
    private lateinit var stderrReader:BufferedReader
    init {
        Thread{
            process = Runtime.getRuntime().exec(command)
            stdinWriter = OutputStreamWriter(process.outputStream)
            stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
            stderrReader = BufferedReader(InputStreamReader(process.errorStream))
        }.start()
    }

    fun writeInput(input: String) {
        GlobalScope.launch(Dispatchers.IO) {
            stdinWriter.write(input)
            stdinWriter.flush()
        }
    }

    fun readOutput(): String {
        return stdoutReader.readLine() ?: ""
    }

    fun readError(): String {
        return stderrReader.readLine() ?: ""
    }

    fun stop() {
        process.destroy()
    }
}
