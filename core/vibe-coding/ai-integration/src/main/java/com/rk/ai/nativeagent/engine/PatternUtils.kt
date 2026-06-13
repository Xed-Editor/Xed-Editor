package com.rk.ai.nativeagent.engine

fun patternMatches(pattern: String, input: String): Boolean {
    if (pattern == "*") return true
    if (pattern == input) return true
    val regex = pattern
        .replace(".", "\\.")
        .replace("*", ".*")
        .toRegex(RegexOption.IGNORE_CASE)
    return regex.matches(input)
}
