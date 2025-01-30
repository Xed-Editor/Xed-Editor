package com.rk.runner

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.libcommons.application
import com.rk.libcommons.runOnUiThread
import com.rk.libcommons.toast
import com.rk.runner.runners.node.NodeRunner
import com.rk.runner.runners.python.PythonRunner
import com.rk.runner.runners.shell.ShellRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkDownRunner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File


interface RunnerImpl {
    fun run(file: File, context: Context)
    fun getName(): String
    fun getDescription(): String
    fun getIcon(context: Context): Drawable?
    fun isRunning(): Boolean
    fun stop()
}


object Runner {
    private val runnable_ext = hashSetOf("html","md","py","mjs","js","sh","bash")

    private fun getRunnerInstance(ext:String):List<RunnerImpl>{
        return when(ext){
            "html" -> listOf(HtmlRunner())
            "md" -> listOf(MarkDownRunner())
            "py" -> listOf(PythonRunner())
            "mjs","js" -> listOf(NodeRunner())
            "sh","bash" -> listOf(ShellRunner())
            else -> emptyList()
        }
    }

    fun isRunnable(file: File): Boolean {
        val ext = file.name.substringAfterLast('.', "").trim()
        return runnable_ext.contains(ext)
    }

    suspend fun run(file: File, context: Context) {
        withContext(Dispatchers.Default) {
            if (isRunnable(file)) {
                val ext = file.name.substringAfterLast('.', "")


                val runners = getRunnerInstance(ext)
                if (runners.isEmpty()) {
                    toast("No runners are available for this file")
                    return@withContext
                }
                if (runners.size == 1) {
                    withContext(Dispatchers.IO){
                        runCatching { runners[0].run(file, context) }.onFailure {
                           toast(it.message)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showRunnerSelectionDialog(context, runners) { selectedRunner ->
                            Thread {
                                runCatching { selectedRunner.run(file, context) }.onFailure {
                                    toast(it.message)
                                }
                            }.start()
                        }
                    }
                }
            }else{
                toast("This file is not runnable")
            }

        }
    }

    private fun showRunnerSelectionDialog(
        context: Context,
        runners: List<RunnerImpl>,
        onRunnerSelected: (RunnerImpl) -> Unit,
    ) {
        val dialogView =
            LayoutInflater.from(context).inflate(R.layout.dialog_runner_selection, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.runner_recycler_view)

        recyclerView.layoutManager = LinearLayoutManager(context)

        val dialog =
            MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.choose_runtime))
                .setView(dialogView).setNegativeButton(context.getString(R.string.cancel), null)
                .show()

        recyclerView.adapter = RunnerAdapter(runners, dialog, onRunnerSelected)
    }
}
