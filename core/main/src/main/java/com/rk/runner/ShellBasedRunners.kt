package com.rk.runner

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localDir
import com.rk.file.runnerDir
import com.rk.exec.TerminalCommand
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.exec.launchInternalTerminal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object ShellBasedRunners{
    val runners = mutableStateListOf<ShellBasedRunner>()

    init {
        DefaultScope.launch{
            indexRunners()
        }
    }

    suspend fun newRunner(runner: ShellBasedRunner): Boolean {
        return withContext(Dispatchers.IO) {
            if (runners.find { it.getName() == runner.getName() } == null) {
                withContext(Dispatchers.Main) {
                    runners.add(runner)
                }
                runnerDir().child("${runner.getName()}.sh").createFileIfNot().writeText("echo \"This runner has no implementation. Click the runner and add your own script.\"")
                saveRunners()
                true
            } else {
                false
            }
        }
    }

    suspend fun saveRunners() {
        val json = Gson().toJson(runners)
        localDir().child("runners.json").writeText(json)
    }

    suspend fun deleteRunner(runner: ShellBasedRunner, deleteScript: Boolean = true) {
        runners.remove(runner)
        saveRunners()
        runnerDir().child("${runner.getName()}.sh").createFileIfNot().delete()
    }

    suspend fun indexRunners() {
        withContext(Dispatchers.IO) {
            val file = localDir().child("runners.json")
            if (file.exists()) {
                val content = file.readText()
                val type = object : TypeToken<List<ShellBasedRunner>>() {}.type
                runners.clear()
                runners.addAll(Gson().fromJson<List<ShellBasedRunner>>(content, type))
            }
        }
    }
}

data class ShellBasedRunner(private val name: String,val regex: String): RunnerImpl(){
    override fun run(context: Context, fileObject: FileObject) {
        val script = runnerDir().child("${name}.sh").createFileIfNot()
        launchInternalTerminal(
            context, TerminalCommand(
                exe = "/bin/bash",
                args = arrayOf(script.absolutePath, fileObject.getAbsolutePath()),
                id = name,
            )
        )
    }

    override fun getName(): String {
        return name
    }

    fun getScript(): File {
        return runnerDir().child("${getName()}.sh").createFileIfNot()
    }

    override fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }

    override fun isRunning(): Boolean {
        return false
    }

    override fun stop() {

    }
}