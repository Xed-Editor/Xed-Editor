package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.exec.ShellUtils
import com.rk.ai.IdeBridge
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

class GitService {

    suspend fun getGitStatus(workspacePath: String): JsonObject {
        val result = JsonObject()
        if (workspacePath.isBlank()) return result.apply { addProperty("error", "workspacePath required") }
        withContext(Dispatchers.IO) {
            runCatching {
                val repoDir = File(workspacePath)
                val builder = FileRepositoryBuilder().readEnvironment().findGitDir(repoDir)
                val repo = builder.build()
                if (repo.directory == null) { result.addProperty("error", "not a git repository"); return@withContext }
                Git(repo).use { git ->
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
                }
            }.onFailure { result.addProperty("error", it.message ?: "git error") }
        }
        return result
    }

    suspend fun getGitDiff(workspacePath: String): String {
        if (workspacePath.isBlank()) return "workspacePath required"
        return withContext(Dispatchers.IO) {
            runCatching {
                val repoDir = File(workspacePath)
                val builder = FileRepositoryBuilder().readEnvironment().findGitDir(repoDir)
                val repo = builder.build()
                if (repo.directory == null) return@withContext "not a git repository"
                Git(repo).use { git ->
                    val diff = git.diff().call()
                    val baos = java.io.ByteArrayOutputStream()
                    val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                    formatter.setRepository(repo)
                    formatter.format(diff)
                    formatter.close()
                    baos.toString(Charsets.UTF_8.name()).ifEmpty { "no changes" }
                }
            }.getOrElse { "error: ${it.message}" }
        }
    }
}
