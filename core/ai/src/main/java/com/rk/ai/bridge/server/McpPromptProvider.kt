package com.rk.ai.bridge.server

import android.util.Log
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.types.GetPromptResult
import io.modelcontextprotocol.kotlin.sdk.types.PromptArgument
import io.modelcontextprotocol.kotlin.sdk.types.PromptMessage
import io.modelcontextprotocol.kotlin.sdk.types.Role

object McpPromptProvider {
    private const val TAG = "McpPromptProvider"

    fun registerPrompts(server: Server) {
        server.addPrompt(
            name = "code_review",
            description = "Review code for bugs, style issues, and improvements",
            arguments = listOf(
                PromptArgument(name = "code", description = "The code to review", required = true),
                PromptArgument(name = "language", description = "Programming language (e.g. kotlin, java)", required = false),
                PromptArgument(name = "focus", description = "Focus area: security, performance, readability, all", required = false),
            ),
        ) { request ->
            val code = request.params.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            val language = request.params.arguments?.get("language")?.jsonPrimitive?.content ?: "auto-detect"
            val focus = request.params.arguments?.get("focus")?.jsonPrimitive?.content ?: "all"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = io.modelcontextprotocol.kotlin.sdk.types.TextContent(
                            text = "Please review the following $language code with focus on $focus:\n\n```$language\n$code\n```\n\nProvide:\n1. Bug findings\n2. Style issues\n3. Performance concerns\n4. Security vulnerabilities\n5. Suggestions for improvement",
                        ),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = "explain_code",
            description = "Explain what a piece of code does, line by line",
            arguments = listOf(
                PromptArgument(name = "code", description = "The code to explain", required = true),
                PromptArgument(name = "language", description = "Programming language", required = false),
                PromptArgument(name = "depth", description = "Explanation depth: brief, detailed, beginner", required = false),
            ),
        ) { request ->
            val code = request.params.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            val language = request.params.arguments?.get("language")?.jsonPrimitive?.content ?: "auto-detect"
            val depth = request.params.arguments?.get("depth")?.jsonPrimitive?.content ?: "detailed"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = io.modelcontextprotocol.kotlin.sdk.types.TextContent(
                            text = "Explain the following $language code at a $depth level:\n\n```$language\n$code\n```\n\nProvide:\n1. Overall purpose\n2. Line-by-line explanation\n3. Key concepts used\n4. How it interacts with the rest of the codebase",
                        ),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = "generate_test",
            description = "Generate unit tests for a piece of code",
            arguments = listOf(
                PromptArgument(name = "code", description = "The code to generate tests for", required = true),
                PromptArgument(name = "language", description = "Programming language", required = false),
                PromptArgument(name = "framework", description = "Test framework (e.g. JUnit5, kotlin.test)", required = false),
                PromptArgument(name = "coverage", description = "Coverage level: basic, comprehensive, edge-cases", required = false),
            ),
        ) { request ->
            val code = request.params.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            val language = request.params.arguments?.get("language")?.jsonPrimitive?.content ?: "auto-detect"
            val framework = request.params.arguments?.get("framework")?.jsonPrimitive?.content ?: "default"
            val coverage = request.params.arguments?.get("coverage")?.jsonPrimitive?.content ?: "comprehensive"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = io.modelcontextprotocol.kotlin.sdk.types.TextContent(
                            text = "Generate $coverage unit tests for the following $language code using $framework:\n\n```$language\n$code\n```\n\nRequirements:\n1. Test all public methods\n2. Include edge cases\n3. Use proper assertions\n4. Follow testing best practices\n5. Add descriptive test names",
                        ),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = "refactor",
            description = "Suggest refactoring improvements for code",
            arguments = listOf(
                PromptArgument(name = "code", description = "The code to refactor", required = true),
                PromptArgument(name = "language", description = "Programming language", required = false),
                PromptArgument(name = "goal", description = "Refactoring goal: readability, performance, maintainability, testability", required = false),
            ),
        ) { request ->
            val code = request.params.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            val language = request.params.arguments?.get("language")?.jsonPrimitive?.content ?: "auto-detect"
            val goal = request.params.arguments?.get("goal")?.jsonPrimitive?.content ?: "readability"
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = io.modelcontextprotocol.kotlin.sdk.types.TextContent(
                            text = "Refactor the following $language code to improve $goal:\n\n```$language\n$code\n```\n\nProvide:\n1. Refactored code\n2. Explanation of changes\n3. Before/after comparison\n4. Any trade-offs",
                        ),
                    ),
                ),
            )
        }

        server.addPrompt(
            name = "debug",
            description = "Help debug an error or unexpected behavior",
            arguments = listOf(
                PromptArgument(name = "error", description = "The error message or description of the problem", required = true),
                PromptArgument(name = "code", description = "Relevant code snippet", required = false),
                PromptArgument(name = "language", description = "Programming language", required = false),
                PromptArgument(name = "context", description = "Additional context (stack trace, logs, etc.)", required = false),
            ),
        ) { request ->
            val error = request.params.arguments?.get("error")?.jsonPrimitive?.content ?: ""
            val code = request.params.arguments?.get("code")?.jsonPrimitive?.content ?: ""
            val language = request.params.arguments?.get("language")?.jsonPrimitive?.content ?: "auto-detect"
            val context = request.params.arguments?.get("context")?.jsonPrimitive?.content ?: ""
            val prompt = buildString {
                append("Help debug this error:\n\nError: $error\n")
                if (code.isNotBlank()) append("\nRelevant code:\n```$language\n$code\n```\n")
                if (context.isNotBlank()) append("\nAdditional context:\n$context\n")
                append("\nProvide:\n1. Root cause analysis\n2. Step-by-step debugging approach\n3. Potential fixes\n4. Prevention strategies")
            }
            GetPromptResult(
                messages = listOf(
                    PromptMessage(
                        role = Role.user,
                        content = io.modelcontextprotocol.kotlin.sdk.types.TextContent(text = prompt),
                    ),
                ),
            )
        }

        if (com.rk.xededitor.BuildConfig.DEBUG) {
            Log.d(TAG, "Registered 5 MCP prompts: code_review, explain_code, generate_test, refactor, debug")
        }
    }
}
