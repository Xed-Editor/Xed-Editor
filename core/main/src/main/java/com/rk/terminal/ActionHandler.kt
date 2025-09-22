package com.rk.terminal

import androidx.lifecycle.lifecycleScope
//import com.rk.extension.ExtensionManager
import com.rk.file.FileWrapper
import com.rk.file.sandboxDir
import com.rk.libcommons.application
import com.rk.terminal.bridge.Bridge
import com.rk.xededitor.ui.activities.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

object ActionHandler {
    private fun getCorrectPath(args: String,pwd: String): File {
        val sandbox = sandboxDir()

        var preFile = if (args.startsWith("/")) {
            File(sandbox, args.removePrefix("/"))
        } else {
            File(sandbox, "${pwd.removePrefix("/")}/$args")
        }

        val relative = preFile.relativeTo(sandbox).path
        var insidePath = "/$relative"

        getDefaultBindings().forEach {
            if (it.inside != null && insidePath.startsWith(it.inside)) {
                insidePath = insidePath.replaceFirst(it.inside, it.outside)
            }
        }

        if (insidePath.startsWith("/")){
            insidePath = insidePath.replaceFirst("/","${sandboxDir().absolutePath}/")
        }


        return File(insidePath)
    }

    suspend fun onAction(data: String): String {
        runCatching {
            val json = JSONObject(data)
            val action = json.getString("action")
            val args = json.getString("args")
            val pwd = json.getString("pwd")

            val file = getCorrectPath(args = args, pwd = pwd)

            when (action) {
                "edit" -> {
                    if (args.isEmpty()) {
                        return "No file path provided"
                    }

                    if (file.exists().not()) {
                        return "File not found : ${file.absolutePath}"
                    }
                    if (file.isDirectory) {
                        return "Path is a directory : ${file.absolutePath}"
                    }

                    MainActivity.instance?.apply {
                        lifecycleScope.launch(Dispatchers.Main) {
                            viewModel.newTab(FileWrapper(file))
                        }
                    }
                }

                "plugin-install" -> {
                    if (args.isEmpty()) {
                        return "No file path provided"
                    }

                    if (file.exists().not()) {
                        return "File not found : ${file.absolutePath}"
                    }
                    if (file.isDirectory) {
                        return "Path is a directory : ${file.absolutePath}"
                    }
                    if (file.extension != "zip") {
                        return "File is not an ZIP : ${file.absolutePath}"
                    }

//                    runCatching {
//                        ExtensionManager(application!!).installExtension(file)
//                    }.onFailure {
//                        return it.message.toString()
//                    }.onSuccess {
//                        return "ok"
//                    }
                }

                "exec" -> {
                    if (args.isEmpty()) {
                        return "No command provided"
                    }

                    return try {
                        val cmd = args.split(" ")

                        val process = ProcessBuilder(cmd)
                            .redirectErrorStream(true)
                            .start()

                        val output = process.inputStream.bufferedReader().readText()
                        val exitCode = process.waitFor()

                        "ExitCode: $exitCode\n$output"
                    } catch (e: Exception) {
                        e.message.toString()
                    }
                }

                "help" -> {
                    return """
            Available actions:
            - edit <path>           Open a file in the editor
            - plugin-install <path> Install a plugin from a file
            - exec <command>        Execute a command inside sandbox
            - help                  Show this help message
        """.trimIndent()
                }

                else -> {
                    return "Unknown action : $action, use 'xed help' to see available actions"
                }
            }

        }.onFailure {
            return it.message ?: Bridge.RESULT.ERR
        }
        return Bridge.RESULT.OK
    }
}