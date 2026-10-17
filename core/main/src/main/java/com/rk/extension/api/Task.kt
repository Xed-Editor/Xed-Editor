package com.rk.extension.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.rk.activities.main.ui.TaskOutputState
import com.rk.utils.parseAnsiToAnnotatedString
import com.rk.utils.stripAnsi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Properties

/** Interface for a task that can be shown in the floating output view. */
@XedExtensionPoint
abstract class Task {
    abstract val id: String
    abstract val label: String
    abstract val icon: @Composable () -> Unit
    abstract val isRunning: Boolean
    abstract val output: List<OutputLine>
    abstract val latestLine: String
    abstract val progress: Float? // null for indeterminate

    abstract fun stop()

    abstract fun dismiss()

    /** Builds the output for the given terminal theme. Call this from composition. */
    fun buildOutput(props: Properties): AnnotatedString = buildAnnotatedString {
        for (line in output) {
            when (line) {
                is OutputLine.Ansi -> append(parseAnsiToAnnotatedString(line.raw, props))
                is OutputLine.Plain -> append(line.text)
                is OutputLine.Styled ->
                    append(AnnotatedString(line.text, spanStyle = line.style, paragraphStyle = line.paragraphStyle))
            }
            append("\n")
        }
    }
}

sealed interface OutputLine {
    data class Ansi(val raw: String) : OutputLine

    data class Plain(val text: String) : OutputLine

    data class Styled(
        val text: String,
        val style: SpanStyle,
        val paragraphStyle: ParagraphStyle?,
    ) : OutputLine
}

/** Base implementation of a Task. */
@XedExtensionPoint
open class BaseTask(
    override val id: String,
    override val label: String,
    override val icon: @Composable () -> Unit,
) : Task() {
    override var isRunning by mutableStateOf(true)
    override var output = mutableStateListOf<OutputLine>()
    override var latestLine by mutableStateOf("")
    override var progress by mutableStateOf<Float?>(null)

    override fun stop() {
        isRunning = false
    }

    /** Similar to [stop], but should be called when the task is finished. */
    fun finish() {
        isRunning = false
        latestLine = ""
    }

    override fun dismiss() {
        TaskRegistry.removeTask(this)
    }

    fun updateOutput(line: String) {
        updateOutput(line, true)
    }

    /** Appends a line of text to the output, parsing ANSI color escape sequences if enabled. */
    fun updateOutput(line: String, parseAnsi: Boolean = true) {
        if (parseAnsi) {
            output += OutputLine.Ansi(line)
            latestLine = stripAnsi(line)
        } else {
            output += OutputLine.Plain(line)
            latestLine = line
        }
    }

    /** Appends a line of text with a custom [SpanStyle] and [ParagraphStyle] to the output. */
    fun updateOutput(line: String, style: SpanStyle, paragraphStyle: ParagraphStyle? = null) {
        output += OutputLine.Styled(line, style, paragraphStyle)
        latestLine = line
    }

    /** Clears output and latest line. */
    fun clearOutput() {
        output.clear()
        latestLine = ""
    }
}

/** Registry for tasks shown in the floating output view. */
@XedExtensionPoint
object TaskRegistry {
    private val _tasks = MutableStateFlow(listOf<Task>())
    val tasks: StateFlow<List<Task>>
        get() = _tasks.asStateFlow()

    fun addTask(task: Task) {
        _tasks.update { tasks ->
            val existingTask = tasks.find { it.id == task.id }

            if (existingTask?.isRunning == true) {
                return@update tasks
            }

            tasks.filterNot { it.id == task.id } + task
        }
        TaskOutputState.updateActiveTask()
    }

    fun removeTask(task: Task) {
        _tasks.update { it - task }
        TaskOutputState.updateActiveTask()
    }
}
