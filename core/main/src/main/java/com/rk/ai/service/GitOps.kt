package com.rk.ai.service

import com.google.gson.JsonObject

interface GitOps {
    suspend fun getGitStatus(workspacePath: String): JsonObject
    suspend fun getGitDiff(workspacePath: String): String
    suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String
    suspend fun gitCheckout(workspacePath: String, target: String): String
}
