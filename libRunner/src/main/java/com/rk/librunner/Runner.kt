package com.rk.librunner

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.librunner.runners.jvm.beanshell.BeanshellRunner
import com.rk.librunner.runners.node.NodeRunner
import com.rk.librunner.runners.shell.ShellRunner
import com.rk.librunner.runners.web.html.HtmlRunner
import com.rk.librunner.runners.python.PythonRunner
import com.rk.librunner.runners.web.markdown.MarkDownRunner
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

interface RunnerImpl {
  fun run(file: File, context: Context)
  fun getName(): String
  fun getDescription(): String
  fun getIcon(context: Context): Drawable?
}

object Runner {
  val registry = HashMap<String, MutableList<RunnerImpl>>()
  
  init {
    registry["bsh"] = mutableListOf(BeanshellRunner())
    registry["html"] = mutableListOf(HtmlRunner())
    registry["md"] = mutableListOf(MarkDownRunner())
    registry["py"] = mutableListOf(PythonRunner())

    mutableListOf<RunnerImpl>(NodeRunner()).let {
      registry["mjs"] = it
      registry["js"] = it
    }
    mutableListOf<RunnerImpl>(ShellRunner(true), ShellRunner(false)).let {
      registry["sh"] = it
      registry["bash"] = it
    }
  }
  
  fun isRunnable(file: File): Boolean {
    val ext = file.name.substringAfterLast('.', "")
    return registry.keys.any { it == ext }
  }
  
  @OptIn(DelicateCoroutinesApi::class)
  fun run(file: File, context: Context) {
    GlobalScope.launch(Dispatchers.Default) {
      if (isRunnable(file)) {
        val ext = file.name.substringAfterLast('.', "")
        val runners = registry[ext]
        if (runners?.size!! == 0) {
          return@launch
        }
        if (runners.size == 1) {
          Thread {
            runners[0].run(file, context)
          }.start()
          
        } else {
          withContext(Dispatchers.Main) {
            showRunnerSelectionDialog(context, runners) { selectedRunner ->
              Thread {
                selectedRunner.run(file, context)
              }.start()
              
            }
          }
          
        }
      }
    }
    
  }
  
  private fun showRunnerSelectionDialog(
    context: Context, runners: List<RunnerImpl>, onRunnerSelected: (RunnerImpl) -> Unit
  ) {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_runner_selection, null)
    val recyclerView: RecyclerView = dialogView.findViewById(R.id.runner_recycler_view)
    
    recyclerView.layoutManager = LinearLayoutManager(context)
    
    val dialog = MaterialAlertDialogBuilder(context).setTitle(context.getString(R.string.choose_runtime)).setView(dialogView)
      .setNegativeButton(context.getString(R.string.cancel), null).show()
    
    recyclerView.adapter = RunnerAdapter(runners, dialog, onRunnerSelected)
  }
  
}