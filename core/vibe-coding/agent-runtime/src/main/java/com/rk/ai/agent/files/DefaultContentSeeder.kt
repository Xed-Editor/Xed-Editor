package com.rk.ai.agent.files

import android.content.Context
import android.util.Log
import java.io.File

object DefaultContentSeeder {
    private const val TAG = "DefaultContentSeeder"
    private const val SEED_MARKER = ".seeded_v2"

    fun seedIfNeeded(context: Context) {
        val marker = File(context.filesDir, SEED_MARKER)
        if (marker.exists()) return

        try {
            seedCommands(context)
            seedAgents(context)
            seedSkillIfNeeded(context)
            marker.createNewFile()
            Log.i(TAG, "Default commands and agents seeded successfully")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to seed defaults", e)
        }
    }

    fun forceReseed(context: Context) {
        val marker = File(context.filesDir, SEED_MARKER)
        marker.delete()
        seedIfNeeded(context)
    }

    fun seedSkillIfNeeded(context: Context) {
        val skillsDir = File(context.filesDir, "skills")
        val refSkillDir = File(skillsDir, "reference-patterns")
        val refSkillFile = File(refSkillDir, "SKILL.md")
        if (refSkillFile.exists()) return

        refSkillDir.mkdirs()
        refSkillFile.writeText("""
            |---
            |name: reference-patterns
            |description: Reference AI coding tool patterns from opencode and Claude Code
            |---
            |
            |# Reference Patterns
            |
            |The app's internal `help_resources/` directory contains full source code of opencode
            |and Claude Code AI coding tools. Use these as reference for command formats, agent
            |definitions, skill structures, and workflow patterns.
            |
            |## Key Patterns to Learn
            |
            |- **Commands**: Markdown files with YAML frontmatter (description, model, category)
            |- **Agents**: Markdown files with frontmatter (name, description, tools, model, color)
            |- **Workflows**: Multi-phase guided approaches with todo tracking
            |- **Skills**: Reusable markdown modules loaded from .xed/skills/
            |
            |Read specific files from help_resources/ when you need to understand a pattern.
        """.trimMargin())
    }

    private fun seedCommands(context: Context) {
        val commandsDir = CommandFileLoader.getCommandsDir(context)
        if (!commandsDir.exists()) commandsDir.mkdirs()

        val existing = commandsDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

        val defaults = mapOf(
            "review" to """
                |---
                |description: Review recent code changes for bugs and quality
                |category: Code
                |---
                |
                |Review all uncommitted changes for bugs, quality issues, and security vulnerabilities.
                |Use `getGitDiff` to get the changes, then analyze each file.
                |Report findings with severity ratings and fix suggestions.
            """.trimMargin(),

            "test" to """
                |---
                |description: Run tests and analyze results
                |category: Code
                |---
                |
                |Run the test suite and report failures with fix suggestions.
                |Use `getProjectConfig` to detect the test framework.
                |Run tests with the appropriate command, then analyze failures.
                |Suggest specific fixes for each failing test.
            """.trimMargin(),

            "commit" to """
                |---
                |description: Stage and commit changes with a descriptive message
                |category: Git
                |---
                |
                |Stage all changes and create a descriptive commit message.
                |Use conventional commit format: type(scope): description
                |Valid types: feat, fix, docs, chore, refactor, test
                |
                |## GIT DIFF
                |
                |!`git diff`
                |
                |## GIT DIFF --cached
                |
                |!`git diff --cached`
                |
                |## GIT STATUS --short
                |
                |!`git status --short`
            """.trimMargin(),

            "learn" to """
                |---
                |description: Extract non-obvious learnings from session to AGENTS.md
                |category: Project
                |---
                |
                |Analyze this session and extract non-obvious learnings to add to AGENTS.md files.
                |
                |AGENTS.md files can exist at any directory level. Place learnings as close to the relevant code as possible.
                |
                |What counts as a learning:
                |- Hidden relationships between files or modules
                |- Non-obvious configuration, env vars, or flags
                |- API/tool quirks and workarounds
                |- Build/test commands not in README
                |- Architectural decisions and constraints
                |- Files that must change together
                |
                |Process:
                |1. Review session for discoveries, errors, unexpected connections
                |2. Read existing AGENTS.md files at relevant levels
                |3. Create or update AGENTS.md at the appropriate level
                |4. Keep entries to 1-3 lines per insight
            """.trimMargin(),

            "feature-dev" to """
                |---
                |description: Guided feature development with codebase understanding and architecture focus
                |category: Feature
                |---
                |
                |You are implementing a new feature. Follow the 7-phase workflow:
                |
                |Phase 1: Discovery - Understand requirements
                |Phase 2: Exploration - Analyze codebase patterns
                |Phase 3: Questions - Identify ambiguities
                |Phase 4: Architecture - Design approach
                |Phase 5: Implementation - Build it
                |Phase 6: Review - Quality check
                |Phase 7: Summary - Document changes
                |
                |Use `todowrite` to track each phase.
                |Delegates to sub-agents via `delegateTask` for exploration and review.
            """.trimMargin(),
        )

        for ((name, content) in defaults) {
            if (name !in existing) {
                val file = File(commandsDir, "$name.md")
                file.writeText(content)
                Log.d(TAG, "Seeded command: $name")
            }
        }
    }

    private fun seedAgents(context: Context) {
        val agentsDir = AgentFileLoader.getAgentsDir(context)
        if (!agentsDir.exists()) agentsDir.mkdirs()

        val existing = agentsDir.listFiles()?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()

        val defaults = mapOf(
            "code-explorer" to """
                |---
                |name: code-explorer
                |description: Deeply analyzes codebase features by tracing execution paths and mapping architecture layers
                |color: "#44BA81"
                |---
                |
                |You are an expert code analyst. Trace feature implementations from entry points to data storage.
                |
                |1. Find entry points (APIs, UI, CLI)
                |2. Follow call chains
                |3. Map abstraction layers
                |4. Document key files
                |
                |Output: file:line references, execution flow, component responsibilities.
            """.trimMargin(),

            "code-architect" to """
                |---
                |name: code-architect
                |description: Designs feature architectures by analyzing codebase patterns and providing implementation blueprints
                |color: "#2ECC71"
                |---
                |
                |You are a senior software architect. Design implementation blueprints.
                |
                |1. Analyze existing patterns
                |2. Design the architecture
                |3. Specify every file to create/modify
                |4. Break into implementation phases
                |
                |Output: component design, data flow, build sequence, file list.
            """.trimMargin(),

            "code-reviewer-enhanced" to """
                |---
                |name: code-reviewer-enhanced
                |description: Reviews code for bugs, security issues, and maintainability with parallel analysis focuses
                |color: "#E74C3C"
                |---
                |
                |You are a thorough code reviewer. Analyze code for issues.
                |
                |Focus areas:
                |- Bugs and functional correctness
                |- Security vulnerabilities
                |- Code quality and maintainability
                |- Project conventions compliance
                |- Performance concerns
                |
                |Rate each finding: CRITICAL, HIGH, MEDIUM, LOW
                |Suggest specific fixes for each issue.
            """.trimMargin(),
        )

        for ((name, content) in defaults) {
            if (name !in existing) {
                val file = File(agentsDir, "$name.md")
                file.writeText(content)
                Log.d(TAG, "Seeded agent: $name")
            }
        }
    }
}
