package com.rk.runner

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.libcommons.errorDialog
import com.rk.resources.strings
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkDownRunner
import java.lang.ref.WeakReference
import kotlin.text.Regex


abstract class RunnerImpl(){
    abstract fun run(context: Context,fileObject: FileObject)
    abstract fun getName(): String
    abstract fun getIcon(context: Context): Drawable?
    abstract fun isRunning(): Boolean
    abstract fun stop()
}


var currentRunner = WeakReference<RunnerImpl?>(null)

abstract class RunnerBuilder(val regex: Regex,val clazz: Class<out RunnerImpl>){
    fun build(): RunnerImpl{
        return clazz.getDeclaredConstructor().newInstance()
    }
}

object Runner {
    val runnerBuilders = mutableListOf<RunnerBuilder>()
    init {
        runnerBuilders.apply {
            add(object : RunnerBuilder(regex = Regex(".*\\.html$"), clazz = HtmlRunner::class.java){})
            add(object : RunnerBuilder(regex = Regex(".*\\.md$"), clazz = MarkDownRunner::class.java){})
        }
    }

    suspend fun isRunnable(fileObject: FileObject): Boolean{
        ShellBasedRunners.runners.forEach{
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)){
                return true
            }
        }

        runnerBuilders.forEach{
            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)){
                return true
            }
        }
        return false
    }

    suspend fun run(context: Context,fileObject: FileObject){
        val availableRunners = mutableListOf<RunnerImpl>()

        ShellBasedRunners.runners.forEach{
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)){
                availableRunners.add(it)
            }
        }

        runnerBuilders.forEach{
            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)){
                availableRunners.add(it.build())
            }
        }

        if (availableRunners.isEmpty()){
            errorDialog("No runners available")
            return
        }

        if (availableRunners.size == 1){
            availableRunners[0].run(context,fileObject)
        }else{
            showRunnerSelectionDialog(availableRunners)
        }


    }


    private suspend fun showRunnerSelectionDialog(runners: List<RunnerImpl>) {
        errorDialog("Multi runner support not implemented yet")
    }

    suspend fun onMainActivityResumed(){
        currentRunner.get()?.stop()
    }
}
