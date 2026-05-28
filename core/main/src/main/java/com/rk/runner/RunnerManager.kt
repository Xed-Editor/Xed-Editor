package com.rk.runner

import android.content.Context
import com.rk.extension.XedExtensionPoint
import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.runner.runners.UniversalRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.settings.Settings
import com.rk.utils.errorDialog
import java.lang.ref.WeakReference

abstract class Runner {

    abstract suspend fun run(context: Context, fileObject: FileObject)

    abstract fun getName(): String

    abstract fun getIcon(context: Context): Icon?

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()
}

var currentRunner = WeakReference<Runner?>(null)

abstract class RunnerDefinition(val matcher: (FileObject) -> Boolean, val factory: () -> Runner)

object RunnerManager {
    private val _runnerDefinitions = mutableListOf<RunnerDefinition>()

    val runnerDefinitions: List<RunnerDefinition>
        get() = _runnerDefinitions.toList()

    init {
        val htmlExtensions = BuiltinFileType.HTML.extensions.joinToString("|")
        val markdownExtensions = BuiltinFileType.MARKDOWN.extensions.joinToString("|")

        _runnerDefinitions.apply {
            add(
                object :
                    RunnerDefinition(
                        matcher = {
                            Settings.enable_html_runner && Regex(".*\\.($htmlExtensions|svg)$").matches(it.getName())
                        },
                        factory = { HtmlRunner() },
                    ) {}
            )
            add(
                object :
                    RunnerDefinition(
                        matcher = {
                            Settings.enable_md_runner && Regex(".*\\.($markdownExtensions)$").matches(it.getName())
                        },
                        factory = { MarkdownRunner() },
                    ) {}
            )
            add(
                object :
                    RunnerDefinition(
                        matcher = {
                            Settings.enable_universal_runner &&
                                Regex(
                                        ".*\\.(py|js|ts|java|kt|rs|rb|php|c|cpp|cc|cxx|cs|sh|bash|zsh|fish|pl|lua|r|R|hs|f90|f95|f03|f08|pas|tcl|elm|fsx|fs)$"
                                    )
                                    .matches(it.getName())
                        },
                        factory = { UniversalRunner() },
                    ) {}
            )
        }
    }

    @XedExtensionPoint
    fun registerRunner(runnerDefinition: RunnerDefinition) {
        if (!_runnerDefinitions.contains(runnerDefinition)) {
            _runnerDefinitions.add(runnerDefinition)
        }
    }

    @XedExtensionPoint
    fun unregisterRunner(runnerDefinition: RunnerDefinition) {
        _runnerDefinitions.remove(runnerDefinition)
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                return true
            }
        }

        return runnerDefinitions.any { it.matcher(fileObject) }
    }

    suspend fun run(context: Context, fileObject: FileObject, onMultipleRunners: (List<Runner>) -> Unit) {
        val availableRunners = mutableListOf<Runner>()

        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                availableRunners.add(it)
            }
        }

        runnerDefinitions.forEach {
            if (it.matcher(fileObject)) {
                availableRunners.add(it.factory())
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
}
