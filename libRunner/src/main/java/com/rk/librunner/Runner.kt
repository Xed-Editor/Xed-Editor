package com.rk.librunner

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.rk.librunner.runners.beanshell.BeanshellRunner
import com.rk.librunner.runners.web.markdown.MarkDownRunner
import com.rk.librunner.runners.web.html.HtmlRunner
import java.io.File

interface RunnableInterface {
    fun run(file: File, context:Context)
    fun getName() : String
    fun getDescription():String
    fun getIcon(context: Context): Drawable?
}

object Runner {

    private val registry = HashMap<String,List<RunnableInterface>>()

    init {
        registry["bsh"] = arrayListOf(BeanshellRunner())
        registry["md"] = arrayListOf(MarkDownRunner())
        registry["html"] = arrayListOf(HtmlRunner())
    }

    fun isRunnable(file:File) : Boolean{
        val ext = file.name.substringAfterLast('.', "")
        return registry.keys.any { it == ext }
    }

    fun run(file: File,context:Context){
        if (isRunnable(file)){
            val ext = file.name.substringAfterLast('.', "")
            val runners = registry[ext]
            if (runners?.size!! == 0){ return }
            if (runners.size == 1){
                runners[0].run(file,context)
            }else{
                showRunnerSelectionDialog(context, runners) { selectedRunner ->
                    selectedRunner.run(file,context)
                }
            }
        }
    }
    private fun showRunnerSelectionDialog(context: Context, runners: List<RunnableInterface>, onRunnerSelected: (RunnableInterface) -> Unit) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_runner_selection, null)
        val recyclerView: RecyclerView = dialogView.findViewById(R.id.runner_recycler_view)
        
        recyclerView.layoutManager = LinearLayoutManager(context)
	    
       val dialog = MaterialAlertDialogBuilder(context)
            .setTitle("Choose Runtime")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .show()
	    
	    recyclerView.adapter = RunnerAdapter(runners,dialog, onRunnerSelected)
    }
    
}