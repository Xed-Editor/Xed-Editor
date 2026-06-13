@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)
package com.rk.ai.nativeagent.engine

import com.rk.ai.models.Tool
import com.rk.ai.models.UIMessagePart
import com.google.gson.Gson

/**
 * Manages permission auto-respond rules and tool execution
 * permission checks for the VibeCoding engine.
 */
class PermissionManager {
    private val storedAutoRespondRules = mutableListOf<PermissionAutoRespondRule>(
        // Dangerous tools: ask before executing
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*rm *", action = PermissionAction.DENY, description = "Deny file removal commands"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*rmdir *", action = PermissionAction.DENY, description = "Deny directory removal"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*del *", action = PermissionAction.DENY, description = "Deny file deletion on Windows"),
        PermissionAutoRespondRule(toolPattern = "writeFile", argPattern = "*", action = PermissionAction.ASK, description = "Ask before writing files"),
        PermissionAutoRespondRule(toolPattern = "editFile", argPattern = "*", action = PermissionAction.ASK, description = "Ask before editing files"),
        PermissionAutoRespondRule(toolPattern = "deleteFile", argPattern = "*", action = PermissionAction.ASK, description = "Ask before deleting files"),
        PermissionAutoRespondRule(toolPattern = "moveFile", argPattern = "*", action = PermissionAction.ASK, description = "Ask before moving files"),
        PermissionAutoRespondRule(toolPattern = "renameFile", argPattern = "*", action = PermissionAction.ASK, description = "Ask before renaming files"),
        PermissionAutoRespondRule(toolPattern = "createDirectory", argPattern = "*", action = PermissionAction.ASK, description = "Ask before creating directories"),
        PermissionAutoRespondRule(toolPattern = "gitCommit", argPattern = "*", action = PermissionAction.ASK, description = "Ask before git commit"),
        PermissionAutoRespondRule(toolPattern = "gitPush", argPattern = "*", action = PermissionAction.ASK, description = "Ask before git push"),
        PermissionAutoRespondRule(toolPattern = "gitCheckout", argPattern = "*", action = PermissionAction.ASK, description = "Ask before git checkout"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*", action = PermissionAction.ASK, description = "Ask for shell commands"),
        // Default: auto-approve everything else (read-only tools, search, etc.)
        PermissionAutoRespondRule(toolPattern = "*", argPattern = "*", action = PermissionAction.ALLOW, description = "Auto-approve all other tools")
    )

    val rules: List<PermissionAutoRespondRule> get() = storedAutoRespondRules.toList()

    fun addRule(rule: PermissionAutoRespondRule) {
        storedAutoRespondRules.add(rule)
    }

    fun removeRule(idOrPattern: String) {
        storedAutoRespondRules.removeAll { it.id == idOrPattern || it.toolPattern == idOrPattern }
    }

    fun getAction(toolName: String, inputJson: String): PermissionAction? {
        for (rule in storedAutoRespondRules.reversed()) {
            if (patternMatches(rule.toolPattern, toolName) && patternMatches(rule.argPattern, inputJson)) {
                return rule.action
            }
        }
        return null
    }

    fun isToolMatchedByRule(rule: PermissionAutoRespondRule, toolName: String): Boolean {
        return patternMatches(rule.toolPattern, toolName)
    }

    fun getStaticAction(toolName: String): PermissionAction? {
        val matchingRules = storedAutoRespondRules.filter {
            patternMatches(it.toolPattern, toolName) && it.argPattern == "*"
        }
        return matchingRules.lastOrNull()?.action
    }

    fun wrapToolWithPermissionCheck(tool: Tool, state: () -> VibeCodingState): Tool {
        val staticRuleAction = getStaticAction(tool.name)
        val needsApprovalDefault = when (staticRuleAction) {
            PermissionAction.ALLOW -> false
            PermissionAction.DENY -> false
            PermissionAction.ASK -> true
            null -> tool.needsApproval
        }

        val originalExecute = tool.execute
        val wrappedExecute: suspend (com.google.gson.JsonElement) -> List<UIMessagePart> = { args ->
            val argsStr = try {
                Gson().toJson(args)
            } catch (_: Exception) {
                args.toString()
            }
            val currentAction = state().shouldAutoRespondPermission(tool.name, argsStr)
            if (currentAction == PermissionAction.DENY) {
                listOf(UIMessagePart.Text("Tool '${tool.name}' execution denied by permission rule."))
            } else {
                originalExecute(args)
            }
        }

        return tool.copy(needsApproval = needsApprovalDefault, execute = wrappedExecute)
    }

    private fun patternMatches(pattern: String, input: String): Boolean {
        return com.rk.ai.nativeagent.engine.patternMatches(pattern, input)
    }
}
