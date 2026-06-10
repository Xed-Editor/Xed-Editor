package com.rk.runner

import android.content.Context
import com.rk.file.BuiltinFileType
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.runner.runners.UniversalRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.settings.Settings
import com.rk.utils.errorDialog
import java.lang.ref.WeakReference

abstract class RunnerImpl {

    abstract suspend fun run(context: Context, fileObject: FileObject)

    abstract fun getName(): String

    abstract fun getIcon(context: Context): Icon?

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()
}

var currentRunner = WeakReference<RunnerImpl?>(null)

abstract class RunnerBuilder(
    val regex: Regex,
    val enabled: () -> Boolean = { true },
    val clazz: Class<out RunnerImpl>,
) {
    fun build(): RunnerImpl {
        return clazz.getDeclaredConstructor().newInstance()
    }
}

private fun <T> collectMatchingRunners(
    fileObject: FileObject,
    shellRunners: List<ShellBasedRunner>,
    builderRunners: List<RunnerBuilder>,
    transform: (RunnerImpl) -> T,
): List<T> {
    val name = fileObject.getName()
    val results = mutableListOf<T>()
    shellRunners.forEach { runner ->
        if (Regex(runner.regex).matches(name)) {
            results.add(transform(runner))
        }
    }
    builderRunners.forEach { builder ->
        if (builder.enabled() && builder.regex.matches(name)) {
            results.add(transform(builder.build()))
        }
    }
    return results
}

object Runner {
    val runnerBuilders = mutableListOf<RunnerBuilder>()

    init {
        val htmlExtensions = BuiltinFileType.HTML.extensions.joinToString("|")
        val markdownExtensions = BuiltinFileType.MARKDOWN.extensions.joinToString("|")

        runnerBuilders.apply {
            add(
                object :
                    RunnerBuilder(
                        regex = Regex(".*\\.($htmlExtensions|svg)$"),
                        enabled = { Settings.enable_html_runner },
                        clazz = HtmlRunner::class.java,
                    ) {}
            )
            add(
                object :
                    RunnerBuilder(
                        regex = Regex(".*\\.($markdownExtensions)$"),
                        enabled = { Settings.enable_md_runner },
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
                        enabled = { Settings.enable_universal_runner },
                        clazz = UniversalRunner::class.java,
                    ) {}
            )
        }
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        return collectMatchingRunners(fileObject, ShellBasedRunners.runners, runnerBuilders) { true }
            .isNotEmpty()
    }

    suspend fun run(context: Context, fileObject: FileObject, onMultipleRunners: (List<RunnerImpl>) -> Unit) {
        val availableRunners = collectMatchingRunners(fileObject, ShellBasedRunners.runners, runnerBuilders) { it }

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
