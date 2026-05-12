package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class GitService {

    private data class GitCacheEntry(val git: Git, val createdAt: Long)
    private val repoCache = LinkedHashMap<String, GitCacheEntry>(4, 0.75f, true)
    private val repoCacheTtlMs = 10_000L
    private val repoCacheMaxSize = 8

    private suspend fun getRepo(workspacePath: String): Git? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        repoCache[workspacePath]?.let {
            if (now - it.createdAt < repoCacheTtlMs) return@withContext it.git
            runCatching { it.git.close() }
            repoCache.remove(workspacePath)
        }
        runCatching {
            val repoDir = File(workspacePath)
            val builder = FileRepositoryBuilder().readEnvironment().findGitDir(repoDir)
            val repo = builder.build() ?: return@runCatching null
            if (repo.directory == null) { repo.close(); return@runCatching null }
            val git = Git(repo)
            repoCache[workspacePath] = GitCacheEntry(git, now)
            if (repoCache.size > repoCacheMaxSize) {
                repoCache.keys.firstOrNull()?.let { k ->
                    repoCache[k]?.git?.close(); repoCache.remove(k)
                }
            }
            git
        }.getOrNull()
    }

    suspend fun getGitStatus(workspacePath: String): JsonObject {
        val result = JsonObject()
        if (workspacePath.isBlank()) return result.apply { addProperty("error", "workspacePath required") }
        withContext(Dispatchers.IO) {
            runCatching {
                val git = getRepo(workspacePath) ?: run { result.addProperty("error", "not a git repository"); return@withContext }
                val repo = git.repository
                val status = git.status().call()
                result.addProperty("branch", repo.branch ?: "HEAD")
                result.add("changes", JsonArray().apply {
                    status.added.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "added") }) }
                    status.changed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "staged") }) }
                    status.modified.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "modified") }) }
                    status.removed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "removed") }) }
                    status.untracked.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "untracked") }) }
                    status.conflicting.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "conflicting") }) }
                })
                result.addProperty("totalChanges", result.getAsJsonArray("changes").size())
            }.onFailure { result.addProperty("error", it.message ?: "git error") }
        }
        return result
    }

    suspend fun getGitDiff(workspacePath: String): String {
        if (workspacePath.isBlank()) return "workspacePath required"
        return withContext(Dispatchers.IO) {
            runCatching {
                val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
                val repo = git.repository
                val diff = git.diff().call()
                val baos = java.io.ByteArrayOutputStream()
                val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                formatter.setRepository(repo)
                formatter.format(diff)
                formatter.close()
                baos.toString(Charsets.UTF_8.name()).ifEmpty { "no changes" }
            }.getOrElse { "error: ${it.message}" }
        }
    }
}
