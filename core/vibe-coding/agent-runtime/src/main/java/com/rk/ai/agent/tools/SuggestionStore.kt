package com.rk.ai.agent.tools

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject

object SuggestionStore {
    private val _suggestions = MutableStateFlow<List<JsonObject>>(emptyList())
    val suggestions: StateFlow<List<JsonObject>> = _suggestions

    fun update(suggestions: List<JsonObject>) {
        _suggestions.value = suggestions
    }
}
