package com.rk.ai.bridge

import com.google.gson.JsonObject

fun interface IdeNotificationSender {
    fun sendNotification(method: String, params: JsonObject)
}
