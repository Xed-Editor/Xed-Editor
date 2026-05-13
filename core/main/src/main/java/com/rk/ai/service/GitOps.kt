package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject

interface GitOps {
    suspend fun getGitStatus(workspacePath: String): JsonObject
    suspend fun getGitDiff(workspacePath: String): String
    suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String
    suspend fun gitCheckout(workspacePath: String, target: String, createNew: Boolean = false): String
    suspend fun gitLog(workspacePath: String, maxCount: Int): JsonArray
    suspend fun listGitBranches(workspacePath: String): JsonObject
    suspend fun gitPull(workspacePath: String, rebase: Boolean): String
    suspend fun gitPush(workspacePath: String, force: Boolean): String
    suspend fun gitFetch(workspacePath: String): String
    suspend fun gitCreateBranch(workspacePath: String, branchName: String, startPoint: String?): String
    suspend fun gitStash(workspacePath: String, message: String?): String
    suspend fun gitStashPop(workspacePath: String): String
}