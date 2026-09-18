package com.rk.ai.settings

import com.rk.settings.CachedPreference

object AiSettings {
    var apiKey by CachedPreference("ai_api_key", "")

    var baseUrl by CachedPreference("ai_base_url", "https://api.deepseek.com")

    var modelId by CachedPreference("ai_model_id", "deepseek-chat")

    var systemPrompt by CachedPreference("ai_system_prompt", DEFAULT_SYSTEM_PROMPT)

    var permissionMode by CachedPreference("ai_permission_mode", PermissionMode.ASK.name)

    val chatCompletionsPath: String
        get() =
            if (baseUrl.contains("deepseek", ignoreCase = true)) {
                "chat/completions"
            } else {
                "v1/chat/completions"
            }

    val hasApiKey: Boolean
        get() = apiKey.isNotBlank()

    fun currentPermissionMode(): PermissionMode =
        PermissionMode.entries.firstOrNull { it.name == permissionMode } ?: PermissionMode.ASK

    const val DEFAULT_SYSTEM_PROMPT: String =
        "You are Xed AI, a coding assistant embedded in the Xed Editor Android app. " +
            "Be concise and precise. When you propose code changes, name the exact file paths " +
            "and keep edits minimal and focused."
}

enum class PermissionMode {
    ASK,
    ACCEPT_EDITS,
    PLAN,
    YOLO,
}
