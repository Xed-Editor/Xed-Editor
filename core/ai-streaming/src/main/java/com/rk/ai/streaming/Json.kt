package com.rk.ai.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

val JsonElement.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

val JsonElement.jsonArrayOrNull: JsonArray?
    get() = this as? JsonArray

val JsonElement.jsonPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

fun JsonObject.getByKey(key: String): String {
    return evaluateJsonExpr(key, this)
}

fun String.applyPlaceholders(vararg placeholders: Pair<String, String>): String {
    var result = this
    placeholders.forEach { (key, value) ->
        result = result.replace("{$key}", value)
    }
    return result
}

fun <T> List<T>.checkDifferent(
    other: List<T>,
    eq: (T, T) -> Boolean = { a, b -> a == b }
): Pair<List<T>, List<T>> {
    val toAdd = other.filter { o -> none { eq(o, it) } }
    val toRemove = filter { c -> other.none { eq(c, it) } }
    return toAdd to toRemove
}

fun <T> Flow<T>.toMutableStateFlow(
    scope: CoroutineScope,
    initialValue: T,
): MutableStateFlow<T> {
    val stateFlow = stateIn(scope, SharingStarted.Eagerly, initialValue)
    return MutableStateFlow(initialValue).also { mutable ->
        scope.launch {
            stateFlow.collect { value ->
                mutable.value = value
            }
        }
    }
}
