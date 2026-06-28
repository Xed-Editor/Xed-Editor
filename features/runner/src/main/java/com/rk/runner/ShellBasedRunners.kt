package com.rk.runner

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.TerminalLauncher
import com.rk.file.FileObject
import com.rk.file.child
import com.rk.file.createFileIfNot
import com.rk.file.localDir
import com.rk.icons.Icon
import com.rk.resources.drawables
import java.io.File
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object ShellBasedRunners {
    val runners = mutableStateListOf<ShellBasedRunner>()

    init {
        DefaultScope.launch { indexRunners() }
    }

    suspend fun newRunner(runner: ShellBasedRunner): Boolean {
        return withContext(Dispatchers.IO) {
            if (runners.find { it.label == runner.label } == null) {
                withContext(Dispatchers.Main) { runners.add(runner) }
                runnerDir()
                    .child("${runner.label}.sh")
                    .createFileIfNot()
                    .writeText("echo \"This runner has no implementation. Click the runner and add your own script.\"")
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

    suspend fun deleteRunner(runner: ShellBasedRunner) {
        runners.remove(runner)
        saveRunners()
        runnerDir().child("${runner.label}.sh").createFileIfNot().delete()
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

data class ShellBasedRunner(override val label: String, val regex: String) : Runner() {

    override val id = Random.nextInt().toString()

    override fun matcher(fileObject: FileObject): Boolean {
        return Regex(regex).matches(fileObject.getName())
    }

    override suspend fun run(activity: Activity, fileObject: FileObject) {
        val script = runnerDir().child("${label}.sh").createFileIfNot()
        TerminalLauncher.launch(
            activity = activity,
            exe = "/bin/bash",
            args = arrayOf(script.absolutePath, fileObject.getAbsolutePath()),
            id = label,
        )
    }

    fun getScript(): File {
        return runnerDir().child("$label.sh").createFileIfNot()
    }

    override fun getIcon(context: Context): Icon {
        return Icon.ResourceIcon(drawables.bash)
    }

    override suspend fun isRunning(): Boolean {
        return false
    }

    override suspend fun stop() {}
}
