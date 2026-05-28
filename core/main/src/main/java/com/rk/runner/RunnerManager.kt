package com.rk.runner

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import com.rk.extension.XedExtensionPoint
import com.rk.file.FileObject
import com.rk.icons.Icon
import com.rk.runner.runners.UniversalRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkdownRunner
import com.rk.settings.Preference
import com.rk.utils.errorDialog

abstract class Runner {

    abstract suspend fun run(context: Context, fileObject: FileObject)

    abstract fun getIcon(context: Context): Icon?

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()

    abstract fun matcher(fileObject: FileObject): Boolean

    abstract val id: String
    abstract val label: String
    open val description: String? = null
    open val onConfigure: (() -> Unit)? = null

    fun isEnabled(): Boolean {
        return Preference.getBoolean("runner_$id", true)
    }

    fun setEnabled(enabled: Boolean) {
        Preference.setBoolean("runner_$id", enabled)
    }
}

object RunnerManager {

    private val _extensionRunners = mutableStateListOf<Runner>()

    val extensionRunners: List<Runner>
        get() = _extensionRunners.toList()

    val builtinRunners = listOf(HtmlRunner, MarkdownRunner, UniversalRunner)

    @XedExtensionPoint
    fun registerRunner(runner: Runner) {
        if (!_extensionRunners.contains(runner)) {
            _extensionRunners.add(runner)
        }
    }

    @XedExtensionPoint
    fun unregisterRunner(runner: Runner) {
        _extensionRunners.remove(runner)
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        return getAvailableRunners(fileObject).isNotEmpty()
    }

    fun getAvailableRunners(fileObject: FileObject): List<Runner> {
        val result = mutableListOf<Runner>()

        val runners = builtinRunners + extensionRunners + ShellBasedRunners.runners
        runners.forEach {
            if (it.isEnabled() && it.matcher(fileObject)) {
                result.add(it)
            }
        }

        return result
    }

    suspend fun run(context: Context, fileObject: FileObject, onMultipleRunners: (List<Runner>) -> Unit) {
        val availableRunners = getAvailableRunners(fileObject)

        if (availableRunners.isEmpty()) {
            errorDialog("No runners available", context)
            return
        }

        if (availableRunners.size == 1) {
            availableRunners[0].run(context, fileObject)
        } else {
            onMultipleRunners.invoke(availableRunners)
        }
    }
}
