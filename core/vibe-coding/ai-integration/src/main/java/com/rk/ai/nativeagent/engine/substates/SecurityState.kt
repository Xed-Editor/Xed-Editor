package com.rk.ai.nativeagent.engine

data class SecurityState(
    val alerts: List<SecurityAlert> = emptyList(),
    val autoRespondRules: List<PermissionAutoRespondRule> = emptyList(),
) {
    val hasAlerts: Boolean get() = alerts.isNotEmpty()

    fun shouldAutoRespond(toolName: String, inputJson: String): PermissionAction? {
        for (rule in autoRespondRules.reversed()) {
            if (patternMatches(rule.toolPattern, toolName) && patternMatches(rule.argPattern, inputJson)) {
                return rule.action
            }
        }
        return null
    }

    fun isToolMatchedByRule(rule: PermissionAutoRespondRule, toolName: String): Boolean {
        return patternMatches(rule.toolPattern, toolName)
    }

    private fun patternMatches(pattern: String, input: String): Boolean {
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .toRegex(RegexOption.IGNORE_CASE)
        return regex.matches(input)
    }
}
