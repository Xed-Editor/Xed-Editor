package com.rk.ai.service

import com.google.gson.JsonObject

fun interface IdeNotificationSender {
    fun sendNotification(method: String, params: JsonObject)
}
