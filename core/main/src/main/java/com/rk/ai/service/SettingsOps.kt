package com.rk.ai.service

import com.google.gson.JsonObject

interface SettingsOps {
    fun getSetting(key: String): String?
    fun setSetting(key: String, value: String)
    fun getAllSettings(): JsonObject
}
