package com.rk.ai.bridge.tools

import com.google.gson.JsonObject
import com.rk.ai.bridge.McpToolContext
import com.rk.ai.bridge.McpToolResult

class AgentWorkflowTool : BaseMcpTool() {
    override fun getCategory(): String = "AI Workflows"
    override fun getName(): String = "agentWorkflow"
    override fun getDescription(): String = """Executes pre-built AI agent workflows for common tasks.
Workflows are multi-step processes that combine multiple tools for complex operations."""

    override fun getRequiredParams(): Map<String, String> = mapOf("workflow" to "string")
    override fun getOptionalParams(): Map<String, String> = mapOf(
        "target" to "string",
        "options" to "string"
    )
    override fun getRequiredParamDescriptions(): Map<String, String> = mapOf(
        "workflow" to "Workflow name: 'refactor', 'optimize', 'secure', 'document', 'test', 'migrate', 'debug', 'review'"
    )
    override fun getOptionalParamDescriptions(): Map<String, String> = mapOf(
        "target" to "Target file, function, or module",
        "options" to "JSON string with workflow-specific options"
    )

    override suspend fun executeValidated(args: JsonObject, context: McpToolContext): McpToolResult {
        val workflow = requireString(args, "workflow")
        val target = optionalString(args, "target")
        val options = optionalString(args, "options")

        return when (workflow.lowercase()) {
            "refactor" -> runRefactorWorkflow(context, target, options)
            "optimize" -> runOptimizeWorkflow(context, target, options)
            "secure" -> runSecureWorkflow(context, target, options)
            "document" -> runDocumentWorkflow(context, target, options)
            "test" -> runTestWorkflow(context, target, options)
            "migrate" -> runMigrateWorkflow(context, target, options)
            "debug" -> runDebugWorkflow(context, target, options)
            "review" -> runReviewWorkflow(context, target, options)
            "list" -> listWorkflows()
            else -> McpToolResult.error("Unknown workflow: $workflow. Use 'list' to see available workflows.")
        }
    }

    private fun runRefactorWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Refactor Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Analyze** - Read and understand the target code")
                appendLine("2. **Identify** - Find code smells and improvement opportunities")
                appendLine("3. **Plan** - Create refactoring plan with steps")
                appendLine("4. **Execute** - Apply refactoring changes")
                appendLine("5. **Verify** - Run diagnostics and tests")
                appendLine("6. **Review** - Show diff for user review")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the refactor workflow on $targetDesc:")
                appendLine("- Analyze code for: duplicated code, long methods, large classes, dead code")
                appendLine("- Apply SOLID principles where applicable")
                appendLine("- Extract methods/classes where appropriate")
                appendLine("- Improve naming and organization")
                appendLine("- Ensure all existing tests still pass")
                appendLine("- Generate new tests if needed")
                if (!options.isNullOrBlank()) {
                    appendLine()
                    appendLine("### Options:")
                    appendLine(options)
                }
            },
            mapOf("workflow" to "refactor", "target" to targetDesc)
        )
    }

    private fun runOptimizeWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Optimize Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Profile** - Identify performance bottlenecks")
                appendLine("2. **Analyze** - Find optimization opportunities")
                appendLine("3. **Plan** - Create optimization strategy")
                appendLine("4. **Execute** - Apply optimizations")
                appendLine("5. **Verify** - Test performance improvements")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the optimize workflow on $targetDesc:")
                appendLine("- Look for: N+1 queries, unnecessary allocations, blocking calls")
                appendLine("- Optimize algorithms and data structures")
                appendLine("- Add caching where beneficial")
                appendLine("- Reduce memory allocations")
                appendLine("- Profile before and after changes")
            },
            mapOf("workflow" to "optimize", "target" to targetDesc)
        )
    }

    private fun runSecureWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Security Audit Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Scan** - Identify security vulnerabilities")
                appendLine("2. **Classify** - Categorize by severity (OWASP Top 10)")
                appendLine("3. **Remediate** - Apply security fixes")
                appendLine("4. **Verify** - Confirm fixes are correct")
                appendLine("5. **Report** - Generate security report")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the security audit workflow on $targetDesc:")
                appendLine("- Check for: SQL injection, XSS, CSRF, path traversal")
                appendLine("- Verify: input validation, output encoding, authentication")
                appendLine("- Scan for: hardcoded secrets, insecure crypto, weak algorithms")
                appendLine("- Review: dependency vulnerabilities")
                appendLine("- Apply OWASP security guidelines")
            },
            mapOf("workflow" to "secure", "target" to targetDesc)
        )
    }

    private fun runDocumentWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Documentation Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Analyze** - Understand code structure and purpose")
                appendLine("2. **Plan** - Determine documentation needs")
                appendLine("3. **Generate** - Create documentation")
                appendLine("4. **Review** - Validate accuracy and completeness")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the documentation workflow on $targetDesc:")
                appendLine("- Add/update function docstrings")
                appendLine("- Document class purposes and relationships")
                appendLine("- Add inline comments for complex logic")
                appendLine("- Update README if needed")
                appendLine("- Generate API documentation")
            },
            mapOf("workflow" to "document", "target" to targetDesc)
        )
    }

    private fun runTestWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Test Generation Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Analyze** - Understand code to test")
                appendLine("2. **Identify** - Find test cases needed")
                appendLine("3. **Generate** - Create test code")
                appendLine("4. **Verify** - Run tests and fix issues")
                appendLine("5. **Coverage** - Check test coverage")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the test generation workflow on $targetDesc:")
                appendLine("- Generate unit tests for all public methods")
                appendLine("- Add edge case tests")
                appendLine("- Include integration tests where appropriate")
                appendLine("- Mock external dependencies")
                appendLine("- Ensure tests are isolated and repeatable")
            },
            mapOf("workflow" to "test", "target" to targetDesc)
        )
    }

    private fun runMigrateWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Migration Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Analyze** - Understand current state")
                appendLine("2. **Plan** - Create migration strategy")
                appendLine("3. **Execute** - Apply changes incrementally")
                appendLine("4. **Verify** - Test each step")
                appendLine("5. **Cleanup** - Remove deprecated code")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the migration workflow on $targetDesc:")
                appendLine("- Identify deprecated patterns or APIs")
                appendLine("- Create migration plan with rollback strategy")
                appendLine("- Apply changes in small, testable increments")
                appendLine("- Update dependencies if needed")
                appendLine("- Ensure backward compatibility")
            },
            mapOf("workflow" to "migrate", "target" to targetDesc)
        )
    }

    private fun runDebugWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Debug Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Reproduce** - Understand the issue")
                appendLine("2. **Isolate** - Find root cause")
                appendLine("3. **Fix** - Apply fix")
                appendLine("4. **Verify** - Confirm fix works")
                appendLine("5. **Prevent** - Add tests to prevent regression")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the debug workflow on $targetDesc:")
                appendLine("- Analyze error messages and stack traces")
                appendLine("- Check logs and terminal output")
                appendLine("- Identify potential root causes")
                appendLine("- Apply minimal fix")
                appendLine("- Add regression test")
            },
            mapOf("workflow" to "debug", "target" to targetDesc)
        )
    }

    private fun runReviewWorkflow(context: McpToolContext, target: String?, options: String?): McpToolResult {
        val targetDesc = target ?: "current file"
        return McpToolResult.success(
            buildString {
                appendLine("## Code Review Workflow")
                appendLine("**Target:** $targetDesc")
                appendLine()
                appendLine("### Workflow Steps:")
                appendLine("1. **Read** - Understand the code changes")
                appendLine("2. **Analyze** - Check for issues")
                appendLine("3. **Suggest** - Provide improvement suggestions")
                appendLine("4. **Report** - Generate review report")
                appendLine()
                appendLine("### Instructions for AI Agent:")
                appendLine("Execute the code review workflow on $targetDesc:")
                appendLine("- Check for bugs and logic errors")
                appendLine("- Review security implications")
                appendLine("- Assess performance impact")
                appendLine("- Verify code style consistency")
                appendLine("- Suggest improvements")
            },
            mapOf("workflow" to "review", "target" to targetDesc)
        )
    }

    private fun listWorkflows(): McpToolResult {
        return McpToolResult.success(
            buildString {
                appendLine("## Available Agent Workflows")
                appendLine()
                appendLine("| Workflow | Description |")
                appendLine("|----------|-------------|")
                appendLine("| `refactor` | Improve code structure and quality |")
                appendLine("| `optimize` | Improve performance and efficiency |")
                appendLine("| `secure` | Security audit and remediation |")
                appendLine("| `document` | Generate documentation |")
                appendLine("| `test` | Generate unit and integration tests |")
                appendLine("| `migrate` | Migrate to new APIs or patterns |")
                appendLine("| `debug` | Debug and fix issues |")
                appendLine("| `review` | Code review with suggestions |")
                appendLine("| `list` | Show this help |")
                appendLine()
                appendLine("### Usage:")
                appendLine("```json")
                appendLine("""{
  "workflow": "refactor",
  "target": "path/to/file.kt",
  "options": "{\\"focus\\": \\"performance\\"}"
}""")
                appendLine("```")
            }
        )
    }
}
