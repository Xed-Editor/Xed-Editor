package com.rk.runner

import android.app.Activity
import android.content.Context
import com.rk.DefaultScope
import com.rk.events.Events
import com.rk.extension.api.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.runner.runners.XedProjectRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.settings.Settings
import com.rk.utils.errorDialog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

object RunnerManager {

    private val _extensionRunners = MutableStateFlow<List<Runner>>(emptyList())

    val extensionRunners = _extensionRunners.asStateFlow()

    private val _builtinRunners =
        MutableStateFlow<List<Runner>>(listOf(HtmlRunner, MarkdownRunner, XedProjectRunner))
    val builtinRunners = _builtinRunners.asStateFlow()

    @XedExtensionPoint
    fun registerRunner(runner: Runner) {
        if (!_extensionRunners.value.contains(runner)) {
            _extensionRunners.update { it + runner }
        }
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun addBuiltInRunner(vararg servers: Runner) {
        _builtinRunners.update { it + servers }
    }

    @ApiStatus.Internal
    // TODO: Temp
    fun removeBuiltInRunner(vararg servers: Runner) {
        _builtinRunners.update { list -> list.filterNot { runner -> runner in servers } }
    }

    @XedExtensionPoint
    fun unregisterRunner(runner: Runner) {
        _extensionRunners.update { it - runner }
    }

    fun isRunnable(fileObject: FileObject?, projectRoot: FileObject?): Boolean {
        return getAvailableRunners(fileObject, projectRoot).isNotEmpty()
    }

    fun getAvailableRunners(fileObject: FileObject?, projectRoot: FileObject?): List<Runner> {
        val result = mutableListOf<Runner>()

        val runners = builtinRunners.value + extensionRunners.value + ShellBasedRunners.runners.value
        runners.forEach { runner ->
            if (runner.isEnabled()) {
                when (runner) {
                    is FileRunner if fileObject != null && runner.matcher(fileObject) -> {
                        result.add(runner)
                    }

                    is ProjectRunner if projectRoot != null && runner.matcher(projectRoot) -> {
                        result.add(runner)
                    }
                }
            }
        }

        return result
    }

    fun run(
        activity: Activity,
        fileObject: FileObject?,
        projectRoot: FileObject?,
        forceSelection: Boolean = false,
        beforeRun: () -> Unit = {},
        onMultipleRunners: (List<RunnableOption>) -> Unit,
    ) {
        val availableRunners = getAvailableRunners(fileObject, projectRoot)

        if (availableRunners.isEmpty()) {
            errorDialog(activity, msg = "No runners available")
            return
        }

        if (availableRunners.size == 1 && !forceSelection) {
            DefaultScope.launch {
                beforeRun()
                val runner = availableRunners.first()
                if (runner is FileRunner && fileObject != null) {
                    runner.run(activity, fileObject)
                } else if (runner is ProjectRunner && projectRoot != null) {
                    runner.run(activity, projectRoot)
                }
                Settings.runs += 1
                Events.publish(RunnerEvent.RunnerRun(runner))
            }
        } else {
            val options = availableRunners.map { runner ->
                object : RunnableOption {
                    override val label: String = runner.label

                    override fun getIcon(context: Context): Icon? = runner.getIcon(context)

                    override fun run(activity: Activity) {
                        DefaultScope.launch {
                            beforeRun()
                            if (runner is FileRunner && fileObject != null) {
                                runner.run(activity, fileObject)
                            } else if (runner is ProjectRunner && projectRoot != null) {
                                runner.run(activity, projectRoot)
                            }
                            Settings.runs += 1
                            Events.publish(RunnerEvent.RunnerRun(runner))
                        }
                    }
                }
            }
            onMultipleRunners.invoke(options)
        }
    }
}
