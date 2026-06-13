package com.rk.ai.agent.context

data class ContextBundle(
    val goal: String = "",
    val preferences: List<String> = emptyList(),
    val projectSummary: String = "",
    val projectStructure: String = "",
    val workingState: WorkingState = WorkingState(),
    val historical: List<String> = emptyList(),
    val relevantFiles: List<String> = emptyList(),
    val relevantSymbols: List<String> = emptyList(),
    val recentEdits: List<EditRecord> = emptyList(),
    val sessionLog: List<String> = emptyList(),
) {
    fun isEmpty(): Boolean = goal.isBlank() && projectSummary.isBlank()
    fun toPromptBlock(): String = buildString {
        if (goal.isNotBlank()) appendLine("Current goal: $goal")
        if (projectSummary.isNotBlank()) appendLine("Project: $projectSummary")
        if (relevantFiles.isNotEmpty()) appendLine("Active files: ${relevantFiles.joinToString(", ")}")
        if (relevantSymbols.isNotEmpty()) appendLine("Relevant symbols: ${relevantSymbols.joinToString(", ")}")
        if (recentEdits.isNotEmpty()) {
            appendLine("Recent edits:")
            recentEdits.takeLast(5).forEach { appendLine("  - ${it.file} (${it.action})") }
        }
    }
}

class ContextMemoryManager(
    val conversation: ConversationMemory = ConversationMemory(),
    val project: ProjectMemory = ProjectMemory(),
    val working: WorkingMemory = WorkingMemory(),
) {
    fun getBundle(query: String = ""): ContextBundle {
        return ContextBundle(
            goal = conversation.getCurrentGoal(),
            preferences = conversation.getPreferences(),
            projectSummary = project.getCachedSummary(),
            projectStructure = project.getCachedStructure(),
            workingState = working.getState(),
            historical = conversation.getRelevantFacts(query),
            relevantFiles = if (query.isNotBlank()) project.findFiles(query) else emptyList(),
            relevantSymbols = project.findSymbol(query),
            recentEdits = working.getState().recentEdits,
            sessionLog = working.getRecentLogs(10),
        )
    }

    fun storeProjectInfo(summary: String, structure: String) {
        project.setSummary(summary)
        project.setStructure(structure)
    }

    fun storeFileIndex(path: String, symbols: List<String>, lineCount: Int) {
        project.indexFile(path, symbols, lineCount)
    }

    fun storeSymbol(name: String, filePath: String) {
        project.indexSymbol(name, filePath)
    }

    fun recordEdit(file: String, action: String) {
        working.recordEdit(file, action)
    }

    fun log(message: String) {
        working.log(message)
    }

    fun addFact(fact: String) {
        conversation.addFact(fact)
    }

    fun addPreference(pref: String) {
        conversation.addPreference(pref)
    }

    fun clearAll() {
        conversation.clear()
        project.clear()
        working.clear()
    }
}
