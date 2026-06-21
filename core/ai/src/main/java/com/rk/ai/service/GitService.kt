package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.settings.Settings
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.PersonIdent
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

    private fun invalidateRepoCache(workspacePath: String) {
        repoCache.remove(workspacePath)?.git?.let { runCatching { it.close() } }
    }

    private data class StatusCache(val path: String, val result: JsonObject, val timestamp: Long)
    private var lastStatus: StatusCache? = null

    suspend fun getGitStatus(workspacePath: String): JsonObject {
        val now = System.currentTimeMillis()
        lastStatus?.let {
            if (it.path == workspacePath && now - it.timestamp < 3000) return it.result
        }

        val result = JsonObject()
        if (workspacePath.isBlank()) return result.apply { addProperty("error", "workspacePath required") }
        withContext(Dispatchers.IO) {
            runCatching {
                val git = getRepo(workspacePath) ?: run { result.addProperty("error", "not a git repository"); return@withContext }
                val repo = git.repository
                val status = git.status().call()
                result.addProperty("branch", repo.branch ?: "HEAD")
                result.add("changes", JsonArray().apply {
                    status.added.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "staged_added") }) }
                    status.changed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "staged_modified") }) }
                    status.modified.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "working_tree_modified") }) }
                    status.removed.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "staged_removed") }) }
                    status.missing.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "working_tree_deleted") }) }
                    status.untracked.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "untracked") }) }
                    status.conflicting.forEach { add(JsonObject().apply { addProperty("file", it); addProperty("type", "conflicting") }) }
                })
                result.addProperty("totalChanges", result.getAsJsonArray("changes").size())
            }.onFailure { result.addProperty("error", it.message ?: "git error") }
        }
        lastStatus = StatusCache(workspacePath, result, now)
        return result
    }

    suspend fun getGitDiff(workspacePath: String): String {
        if (workspacePath.isBlank()) return "workspacePath required"
        return withContext(Dispatchers.IO) {
            runCatching {
                val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
                val repo = git.repository

                val output = StringBuilder()

                val status = git.status().call()
                val changedFiles = status.added + status.changed + status.removed +
                    status.missing + status.modified + status.untracked + status.conflicting
                if (changedFiles.isNotEmpty()) {
                    output.appendLine("Changed files:")
                    changedFiles.sorted().forEach { output.appendLine("  $it") }
                    output.appendLine()
                }

                runCatching {
                    val baos = java.io.ByteArrayOutputStream()
                    val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                    formatter.setRepository(repo)

                    runCatching {
                        val unstagedDiff = git.diff().call()
                        formatter.format(unstagedDiff)
                    }.onFailure { e ->
                        if (output.isEmpty()) output.appendLine("unstaged diff error: ${e.message}")
                    }
                    runCatching {
                        val stagedDiff = git.diff().setCached(true).call()
                        formatter.format(stagedDiff)
                    }.onFailure { e ->
                        if (output.isEmpty()) output.appendLine("staged diff error: ${e.message}")
                    }

                    formatter.close()

                    val diffOutput = baos.toString(Charsets.UTF_8.name())
                    if (diffOutput.isNotBlank()) {
                        if (output.isNotEmpty()) output.appendLine("--- diff ---\n")
                        output.append(diffOutput)
                    }
                }.onFailure { e ->
                    if (output.isEmpty()) output.appendLine("diff error: ${e.message}")
                }

                output.toString().ifBlank { "no changes" }
            }.getOrElse { "error: ${it.message}" }
        }
    }

    suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val commit = git.commit()
            commit.setMessage(message)
            if (all) commit.setAll(true)
            val name = Settings.git_name.ifBlank { null }
            val email = Settings.git_email.ifBlank { null }
            if (name != null && email != null) {
                val ident = PersonIdent(name, email)
                commit.setAuthor(ident)
                commit.setCommitter(ident)
            }
            val rev = commit.call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            "committed ${rev.name.take(7)}: $message"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitCheckout(workspacePath: String, target: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            git.checkout().setName(target).call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            "checked out $target"
        }.getOrElse { "error: ${it.message}" }
    }
}
