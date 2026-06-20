package com.rk.ai.agent.executor

import android.util.Log
import com.rk.ai.agent.context.ContextBundle
import com.rk.ai.agent.context.ContextMemoryManager
import com.rk.ai.agent.indexer.ProjectIndexer
import com.rk.ai.agent.indexer.ProjectKnowledgeBase
import com.rk.ai.agent.planner.TaskNode
import com.rk.ai.agent.planner.TaskStatus
import com.rk.ai.agent.planner.TaskTree
import com.rk.ai.agent.review.ActionRecord
import com.rk.ai.agent.review.InfiniteLoopDetector
import com.rk.ai.agent.review.LoopSeverity
import com.rk.ai.agent.review.SelfReviewer
import com.rk.ai.agent.tools.ToolCache
import com.rk.ai.agent.tools.ToolRouter
import com.rk.ai.models.ExecutionState
import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.google.gson.JsonParser
import com.rk.ai.service.IdeService

private const val TAG = "ExecutionEngine"

data class ExecutionResult(
    val taskId: String,
    val success: Boolean,
    val message: String = "",
    val modifiedFiles: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
)

class ExecutionEngine(
    private val ideService: IdeService,
    private val contextMemory: ContextMemoryManager,
    private val toolCache: ToolCache,
    private val toolRouter: ToolRouter,
    private val projectIndexer: ProjectIndexer,
) {
    private val selfReviewer = SelfReviewer()
    private val loopDetector = InfiniteLoopDetector()
    private var knowledgeBase: ProjectKnowledgeBase? = null

    suspend fun initialize(workspacePath: String) {
        val index = projectIndexer.index(workspacePath)
        knowledgeBase = ProjectKnowledgeBase(index)
        for (file in index.files) {
            contextMemory.storeFileIndex(file.path, emptyList(), 0)
        }
        for (sym in index.symbols) {
            contextMemory.storeSymbol(sym.name, sym.file)
        }
        Log.i(TAG, "Indexed ${index.files.size} files, ${index.symbols.size} symbols")
    }

    suspend fun executeTask(
        task: TaskNode,
        tools: List<Tool>,
        generateWithLLM: suspend (String, List<Tool>, ContextBundle) -> String,
    ): ExecutionResult {
        val startTime = System.currentTimeMillis()
        val context = contextMemory.getBundle(task.description)
        val errors = mutableListOf<String>()
        val modifiedFiles = mutableListOf<String>()

        val prompt = buildPrompt(task, context)
        val response = generateWithLLM(prompt, tools, context)

        val toolCalls = extractToolCalls(response)
        for (toolCall in toolCalls) {
            val argsHash = toolCall.input.take(100)
            val cached = toolCache.get(toolCall.name, argsHash)
            if (cached != null) {
                contextMemory.log("Cache hit: ${toolCall.name}")
                toolRouter.recordExecution(toolCall.name, toolCall.input, 0, true, true)
                continue
            }

            val cachedFromMemory = toolRouter.checkMemory(toolCall.name, argsHash)
            if (cachedFromMemory != null) {
                contextMemory.log("Memory hit: ${toolCall.name}")
                toolRouter.recordExecution(toolCall.name, toolCall.input, 0, true, true)
                continue
            }

            val toolDef = tools.find { it.name == toolCall.name }
            if (toolDef == null) {
                errors.add("Tool '${toolCall.name}' not found")
                toolRouter.recordExecution(toolCall.name, toolCall.input, 0, false, false)
                continue
            }

            val execStart = System.currentTimeMillis()
            try {
                val args = JsonParser.parseString(toolCall.input.ifBlank { "{}" })
                val result = toolDef.execute(args)
                val duration = System.currentTimeMillis() - execStart

                toolCache.put(toolCall.name, argsHash, result)
                toolRouter.recordExecution(toolCall.name, toolCall.input, duration, true, false)

                if (toolCall.name in listOf("editFile", "multiEditFile", "writeFile", "createFile", "renameFile")) {
                    val filePath = extractFilePath(toolCall.input)
                    if (filePath != null) {
                        modifiedFiles.add(filePath)
                        contextMemory.recordEdit(filePath, toolCall.name)
                    }
                }

                val review = selfReviewer.reviewToolResults(toolCall.name, toolCall.input, result, ExecutionState.Completed(toolCall.name), context)
                if (!review.passed || review.score < 50) {
                    contextMemory.log("Review: ${toolCall.name} score=${review.score} issues=${review.feedback.take(100)}")
                    if (selfReviewer.shouldRetry(review, 0, 2)) {
                        contextMemory.log("Retrying ${toolCall.name}")
                    }
                }
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - execStart
                toolRouter.recordExecution(toolCall.name, toolCall.input, duration, false, false)
                errors.add("${toolCall.name}: ${e.message}")
                Log.w(TAG, "Tool execution failed: ${toolCall.name}", e)
            }
        }

        loopDetector.record(ActionRecord(
            toolName = task.title,
            inputHash = task.description.hashCode(),
        ))
        val loopInfo = loopDetector.detect()
        if (loopInfo != null && loopInfo.severity == LoopSeverity.CRITICAL) {
            contextMemory.log("LOOP DETECTED: ${loopInfo.description}")
        }

        return ExecutionResult(
            taskId = task.id,
            success = errors.size <= 2,
            message = "Completed in ${System.currentTimeMillis() - startTime}ms",
            modifiedFiles = modifiedFiles.distinct(),
            errors = errors,
        )
    }

    private fun buildPrompt(task: TaskNode, context: ContextBundle): String = buildString {
        appendLine("Task: ${task.title}")
        appendLine("Description: ${task.description}")
        appendLine()
        appendLine("Current Context:")
        append(context.toPromptBlock())
        appendLine()
        appendLine("Available tools are provided. Use them to complete this task.")
        appendLine("After each tool call, check the result and proceed to the next step.")
        appendLine("When done, summarize what was accomplished.")
    }

    private fun extractToolCalls(response: String): List<ToolCallInfo> {
        val calls = mutableListOf<ToolCallInfo>()
        val regex = Regex("""<(\w+_call)>\s*<tool_name>\s*(\w+)\s*</tool_name>\s*<parameters>\s*(\{.*?\})?\s*</parameters>\s*</\1>""", RegexOption.DOT_MATCHES_ALL)
        for (match in regex.findAll(response)) {
            calls.add(ToolCallInfo(match.groupValues[2], match.groupValues[3].ifBlank { "{}" }))
        }
        if (calls.isEmpty()) {
            val simpleRegex = Regex("""(?:use|call|invoke)\s+(\w+)\s*(?:with\s+args?\s*:\s*(\{.*?\}))?""", RegexOption.IGNORE_CASE)
            for (match in simpleRegex.findAll(response)) {
                calls.add(ToolCallInfo(match.groupValues[1], match.groupValues[2].ifBlank { "{}" }))
            }
        }
        return calls.distinctBy { it.name }
    }

    private fun extractFilePath(input: String): String? {
        val patterns = listOf(
            Regex("""filePath["\s:=]+([^"\,}\s]+)"""),
            Regex("""path["\s:=]+([^"\,}\s]+)"""),
        )
        for (pattern in patterns) {
            val match = pattern.find(input)
            if (match != null) {
                val path = match.groupValues[1].trim()
                if (path.startsWith("/") || path.contains(".")) return path
            }
        }
        return null
    }
}

data class ToolCallInfo(
    val name: String,
    val input: String,
)