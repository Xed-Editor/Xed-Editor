package com.rk.ai.agent.hooks

data class SecurityPattern(
    val pattern: Regex,
    val severity: SecuritySeverity,
    val description: String,
    val suggestion: String,
)

enum class SecuritySeverity { LOW, MEDIUM, HIGH, CRITICAL }

class SecurityHook : ToolHook {

    private val patterns = listOf(
        SecurityPattern(
            Regex("(?i)yaml\\.load\\(|yaml\\.load_all\\("),
            SecuritySeverity.HIGH,
            "Unsafe YAML deserialization - can lead to remote code execution",
            "Use yaml.safe_load() instead",
        ),
        SecurityPattern(
            Regex("(?i)pickle\\.load\\(|pickle\\.loads\\(|cPickle\\.loads?\\("),
            SecuritySeverity.CRITICAL,
            "Unsafe pickle deserialization - can execute arbitrary code",
            "Use a safer serialization format like JSON or Protocol Buffers",
        ),
        SecurityPattern(
            Regex("(?i)(?:innerHTML|outerHTML|dangerouslySetInnerHTML)\\s*="),
            SecuritySeverity.HIGH,
            "Direct HTML injection (XSS risk)",
            "Use safe DOM APIs like textContent or a sanitization library",
        ),
        SecurityPattern(
            Regex("""(?i)(?:password|secret|api[_-]?key|token|credential)\s*[:=]\s*['\"][^'"]+['\"]"""),
            SecuritySeverity.CRITICAL,
            "Hardcoded credential detected",
            "Use environment variables or a secret manager instead",
        ),
        SecurityPattern(
            Regex("(?i)exec\\(|eval\\(|Runtime\\.getRuntime\\(\\.exec|ProcessBuilder\\("),
            SecuritySeverity.HIGH,
            "Dynamic code execution detected",
            "Avoid executing arbitrary strings as code",
        ),
        SecurityPattern(
            Regex("""(?i)(?:cmd|command)\s*=\s*['\"](?:rm\s+-rf|rmdir\s+/s|del\s+/f)"""),
            SecuritySeverity.CRITICAL,
            "Destructive filesystem command detected",
            "Verify this is intended and the path is controlled",
        ),
        SecurityPattern(
            Regex("(?i)sql\\s*=\\s*['\"].*\\$\\{|f['\"]\\{.*\\b(?:select|insert|update|delete)\\b"),
            SecuritySeverity.HIGH,
            "SQL injection risk - string interpolation in query",
            "Use parameterized queries or prepared statements",
        ),
        SecurityPattern(
            Regex("""(?i)\.\./|\.\.\\"""),
            SecuritySeverity.MEDIUM,
            "Path traversal pattern detected",
            "Verify the resolved path is within allowed boundaries",
        ),
    )

    override suspend fun evaluate(context: HookContext): HookResult {
        val content = context.newContent ?: context.args["content"]?.toString() ?: return HookResult.Allow

        val findings = patterns.filter { it.pattern.containsMatchIn(content) }

        if (findings.isEmpty()) return HookResult.Allow

        val highestSeverity = findings.maxOf { it.severity }
        val messages = findings.joinToString("\n") {
            "[${it.severity.name}] ${it.description}\n  Suggestion: ${it.suggestion}"
        }

        return when {
            highestSeverity >= SecuritySeverity.CRITICAL -> HookResult.Block(
                "Security check blocked: Potential security issues detected:\n$messages"
            )
            highestSeverity >= SecuritySeverity.HIGH -> HookResult.Warn(
                "Security warning:\n$messages"
            )
            else -> HookResult.Allow
        }
    }
}
