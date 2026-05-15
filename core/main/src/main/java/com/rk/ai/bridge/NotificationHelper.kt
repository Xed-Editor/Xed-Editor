package com.rk.ai.bridge

import com.google.gson.JsonObject

object NotificationHelper {

    const val DIAGNOSTICS_UPDATED = "ide/diagnosticsUpdated"
    const val DIFF_ACCEPTED = "ide/diffAccepted"
    const val DIFF_REJECTED = "ide/diffRejected"
    const val FILE_CHANGED = "ide/fileChanged"
    const val GIT_STATUS_CHANGED = "ide/gitStatusChanged"
    const val SETTINGS_CHANGED = "ide/settingsChanged"
    const val SESSION_STATUS = "ide/sessionStatus"
    const val ERROR = "ide/error"

    fun diagnosticsUpdated(filePath: String, diagnostics: Any): JsonObject = JsonObject().apply {
        addProperty("filePath", filePath)
        add("diagnostics", if (diagnostics is com.google.gson.JsonArray) diagnostics else com.google.gson.JsonArray())
    }

    fun diffAccepted(filePath: String): JsonObject = JsonObject().apply {
        addProperty("filePath", filePath)
    }

    fun diffRejected(filePath: String, reason: String? = null): JsonObject = JsonObject().apply {
        addProperty("filePath", filePath)
        reason?.let { addProperty("reason", it) }
    }

    fun fileChanged(filePath: String, changeType: String = "modified"): JsonObject = JsonObject().apply {
        addProperty("filePath", filePath)
        addProperty("changeType", changeType)
    }

    fun settingsChanged(key: String, value: String): JsonObject = JsonObject().apply {
        addProperty("key", key)
        addProperty("value", value)
    }

    fun sessionStatus(sessionId: String, status: String): JsonObject = JsonObject().apply {
        addProperty("sessionId", sessionId)
        addProperty("status", status)
    }

    fun error(message: String): JsonObject = JsonObject().apply {
        addProperty("message", message)
    }
}
