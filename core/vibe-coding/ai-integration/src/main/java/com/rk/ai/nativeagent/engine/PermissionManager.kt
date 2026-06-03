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
        PermissionAutoRespondRule(toolPattern = "readFile", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow reading files"),
        PermissionAutoRespondRule(toolPattern = "glob", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow finding files"),
        PermissionAutoRespondRule(toolPattern = "grep", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow searching text"),
        PermissionAutoRespondRule(toolPattern = "gitStatus", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow git status"),
        PermissionAutoRespondRule(toolPattern = "gitDiff", argPattern = "*", action = PermissionAction.ALLOW, description = "Allow git diff"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*rm *", action = PermissionAction.DENY, description = "Deny file removal commands"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*git *", action = PermissionAction.ALLOW, description = "Allow Git terminal commands"),
        PermissionAutoRespondRule(toolPattern = "runCommand", argPattern = "*", action = PermissionAction.ASK, description = "Ask for other shell commands"),
        PermissionAutoRespondRule(toolPattern = "*", argPattern = "*", action = PermissionAction.ASK, description = "Ask for all other tools")
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
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(input)
    }
}
