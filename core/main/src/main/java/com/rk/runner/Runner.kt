package com.rk.runner

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.extension.Hooks
import com.rk.file_wrapper.FileObject
import com.rk.file_wrapper.FileWrapper
import com.rk.file_wrapper.UriWrapper
import com.rk.libcommons.toastCatching
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.runner.runners.c.C_Runner
import com.rk.runner.runners.go.GoRunner
import com.rk.runner.runners.node.NodeRunner
import com.rk.runner.runners.python.PythonRunner
import com.rk.runner.runners.shell.ShellRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkDownRunner
import com.rk.xededitor.MainActivity.MainActivity
import com.rk.xededitor.R
import com.rk.xededitor.ui.screens.settings.feature_toggles.InbuiltFeatures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ref.WeakReference


abstract class RunnerImpl{
    abstract fun run(context: Context)
    abstract fun getName(): String
    abstract fun getDescription(): String
    abstract fun getIcon(context: Context): Drawable?
    abstract fun isRunning(): Boolean
    abstract fun stop()
}

var currentRunner = WeakReference<RunnerImpl?>(null)

object Runner {
    private val runnable_ext = hashSetOf("html","md","py","mjs","js","sh","bash","c","cpp","go")

    private fun getRunnerInstance(fileObject: FileObject):List<RunnerImpl>{
        val runners = mutableListOf<RunnerImpl>()

        val ext = fileObject.getName().substringAfterLast(".").trim()

        val openedTabs = MainActivity.activityRef.get()?.adapter?.tabFragments?.keys?.map {
            it.file
        }

        if (openedTabs != null){
            for (i in openedTabs.indices){
                val tab = openedTabs[i]
                if (tab.getName() == "index.html"){
                    runners.add(HtmlRunner(tab))
                    return runners
                }
            }
        }

        var isTermuxFile = false
        val file = if (fileObject is UriWrapper && fileObject.isTermuxUri()){
            isTermuxFile = true
            fileObject.convertToTermuxFile()
        }else if(fileObject is FileWrapper){
            isTermuxFile = false
            fileObject.file
        }else{
            isTermuxFile = false
            null
        }

        //runner that only support native files because uri files cannot be represented as absolute paths
        if (file != null){
            runners.addAll(
                when(ext){
                    "py" -> listOf(PythonRunner(file, isTermuxFile = isTermuxFile))
                    "mjs","js" ->  listOf(NodeRunner(file,isTermuxFile = isTermuxFile))
                    "sh","bash" ->  listOf(ShellRunner(file,isTermuxFile = isTermuxFile))
                    "c","cpp" -> listOf(C_Runner(file,isTermuxFile = isTermuxFile))
                    "go" -> listOf(GoRunner(file,isTermuxFile))
                    else -> emptyList()
                }
            )
        }

        //runners that support uri and native files
        runners.addAll(when(ext){
            "html" -> listOf(HtmlRunner(fileObject))
            "md" -> listOf(MarkDownRunner(fileObject))
            else -> emptyList()
        })

        return runners
    }

    fun isRunnable(file: FileObject):Boolean{
        if (InbuiltFeatures.terminal.state.value.not()){
            return false
        }
        if (file.getName() == "index.html"){
            return true
        }

        Hooks.Runner.runners.values.forEach{ pair ->
            val checker = pair.first
            val result = checker.invoke(file)
            if (result){
                return true
            }
        }


        val ext = file.getName().substringAfterLast('.', "")
        return runnable_ext.contains(ext)
    }

    suspend fun run(file: FileObject, context: Context) {
        withContext(Dispatchers.Default) {
            if (isRunnable(file)) {

                val runners = getRunnerInstance(file).toMutableList()
                runners.addAll(Hooks.Runner.runners.values.mapNotNull { pair -> pair.second.invoke(file,context) })

                if (runners.isEmpty()) {
                    throw RuntimeException("No runners are available for this file")
                }

                if (runners.size == 1) {
                    withContext(Dispatchers.IO){
                        currentRunner = WeakReference(runners[0])
                        toastCatching { runners[0].run(context) }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        showRunnerSelectionDialog(context, runners) { selectedRunner ->
                            Thread {
                                toastCatching {
                                    currentRunner = WeakReference(selectedRunner)
                                    selectedRunner.run(context)

                                }
                            }.start()
                        }
                    }
                }
            }else{
                throw RuntimeException("This file is not runnable")
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

    suspend fun onMainActivityResumed(){
        currentRunner.get()?.stop()
    }
}
