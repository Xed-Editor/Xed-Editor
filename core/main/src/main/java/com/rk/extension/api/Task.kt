package com.rk.extension.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import com.rk.activities.main.ui.TaskOutputState
import com.rk.utils.parseAnsiToAnnotatedString
import com.rk.utils.stripAnsi
import java.util.Properties
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
}

sealed interface OutputLine {
    fun toAnnotatedString(props: Properties): AnnotatedString

    val columns: Int

    class Ansi(val raw: String) : OutputLine {
        override val columns by lazy { stripAnsi(raw).length }

        @Volatile private var cachedProps: Properties? = null
        @Volatile private var cachedAnnotated: AnnotatedString? = null

        override fun toAnnotatedString(props: Properties): AnnotatedString {
            if (cachedProps === props && cachedAnnotated != null) {
                return cachedAnnotated!!
            }
            val parsed = parseAnsiToAnnotatedString(raw, props)
            cachedProps = props
            cachedAnnotated = parsed
            return parsed
        }

        override fun equals(other: Any?): Boolean = other is Ansi && other.raw == raw

        override fun hashCode(): Int = raw.hashCode()
    }

    class Plain(val text: String) : OutputLine {
        override val columns
            get() = text.length

        private val annotated by lazy { AnnotatedString(text) }

        override fun toAnnotatedString(props: Properties): AnnotatedString = annotated

        override fun equals(other: Any?): Boolean = other is Plain && other.text == text

        override fun hashCode(): Int = text.hashCode()
    }

    class Styled(
        val text: String,
        val style: SpanStyle,
        val paragraphStyle: ParagraphStyle?,
    ) : OutputLine {
        override val columns
            get() = text.length

        private val annotated by lazy { AnnotatedString(text, spanStyle = style, paragraphStyle = paragraphStyle) }

        override fun toAnnotatedString(props: Properties): AnnotatedString = annotated

        override fun equals(other: Any?): Boolean =
            other is Styled && other.text == text && other.style == style && other.paragraphStyle == paragraphStyle

        override fun hashCode(): Int = 31 * text.hashCode() + style.hashCode()
    }
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

    /** Appends a line of text to the output, parsing ANSI color escape sequences if enabled. */
    fun updateOutput(line: String, parseAnsi: Boolean = true) {
        if (output.size >= 5500) output.removeRange(0, 500)
        if (parseAnsi) {
            output.add(OutputLine.Ansi(line))
            latestLine = stripAnsi(line)
        } else {
            output.add(OutputLine.Plain(line))
            latestLine = line
        }
    }

    /** Appends a line of text with a custom [SpanStyle] and [ParagraphStyle] to the output. */
    fun updateOutput(line: String, style: SpanStyle, paragraphStyle: ParagraphStyle? = null) {
        if (output.size >= 5500) output.removeRange(0, 500)
        output.add(OutputLine.Styled(line, style, paragraphStyle))
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
