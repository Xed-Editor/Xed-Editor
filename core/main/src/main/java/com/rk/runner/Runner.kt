package com.rk.runner

import android.content.Context
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.runner.runners.UniversalRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.settings.Settings
import com.rk.utils.errorDialog
import java.lang.ref.WeakReference

abstract class RunnerImpl() {

    abstract suspend fun run(context: Context, fileObject: FileObject)

    abstract fun getName(): String

    abstract fun getIcon(context: Context): Icon?

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()
}

var currentRunner = WeakReference<RunnerImpl?>(null)

abstract class RunnerBuilder(val regex: Regex, val enabled: Boolean = true, val clazz: Class<out RunnerImpl>) {
    fun build(): RunnerImpl {
        return clazz.getDeclaredConstructor().newInstance()
    }
}

object Runner {
    val runnerBuilders = mutableListOf<RunnerBuilder>()

    init {
        runnerBuilders.apply {
            add(
                object :
                    RunnerBuilder(
                        regex = Regex(".*\\.(html|svg)$"),
                        enabled = Settings.enable_html_runner,
                        clazz = HtmlRunner::class.java,
                    ) {}
            )
            add(
                object :
                    RunnerBuilder(
                        regex = Regex(".*\\.md$"),
                        enabled = Settings.enable_md_runner,
                        clazz = MarkdownRunner::class.java,
                    ) {}
            )
            add(
                object :
                    RunnerBuilder(
                        regex =
                            Regex(
                                ".*\\.(py|js|ts|java|kt|rs|rb|php|c|cpp|cc|cxx|cs|sh|bash|zsh|fish|pl|lua|r|R|hs|f90|f95|f03|f08|pas|tcl|elm|fsx|fs)$"
                            ),
                        enabled = Settings.enable_universal_runner,
                        clazz = UniversalRunner::class.java,
                    ) {}
            )
        }
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                return true
            }
        }

        runnerBuilders.forEach {
            if (!it.enabled) return@forEach

            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)) {
                return true
            }
        }
        return false
    }

    suspend fun run(context: Context, fileObject: FileObject, onMultipleRunners: (List<RunnerImpl>) -> Unit) {
        val availableRunners = mutableListOf<RunnerImpl>()

        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                availableRunners.add(it)
            }
        }

        runnerBuilders.forEach {
            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)) {
                availableRunners.add(it.build())
            }
        }

        if (availableRunners.isEmpty()) {
            errorDialog("No runners available")
            return
        }

        if (availableRunners.size == 1) {
            availableRunners[0].run(context, fileObject)
        } else {
            onMultipleRunners.invoke(availableRunners)
        }
    }

    suspend fun onMainActivityResumed() {
        currentRunner.get()?.stop()
    }
}
