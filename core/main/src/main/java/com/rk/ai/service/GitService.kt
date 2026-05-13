package com.rk.ai.service

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.rk.settings.Settings
import com.rk.utils.findGitRoot
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

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
        val gitRoot = findGitRoot(workspacePath) ?: return@withContext null
        runCatching {
            val git = Git.open(File(gitRoot))
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

    private fun creds() = UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)

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
                val ref = repo.findRef(Constants.REMOTES_ORIGIN + (repo.branch ?: ""))
                if (ref != null) result.addProperty("aheadBehind", countAheadBehind(git, repo.branch ?: "HEAD"))
            }.onFailure { result.addProperty("error", it.message ?: "git error") }
        }
        lastStatus = StatusCache(workspacePath, result, now)
        return result
    }

    private fun countAheadBehind(git: Git, branch: String): String {
        return try {
            val repo = git.repository
            val localRef = repo.findRef(Constants.R_HEADS + branch)
            val remoteRef = repo.findRef(Constants.REMOTES_ORIGIN + branch)
            if (localRef == null || remoteRef == null) return ""
            org.eclipse.jgit.revwalk.RevWalk(repo).use { walk ->
                val local = walk.parseCommit(localRef.objectId)
                val remote = walk.parseCommit(remoteRef.objectId)
                walk.markStart(local)
                walk.markUninteresting(remote)
                val ahead = walk.count()
                walk.reset()
                walk.markStart(remote)
                walk.markUninteresting(local)
                val behind = walk.count()
                "${ahead}ahead ${behind}behind"
            }
        } catch (_: Exception) { "" }
    }

    suspend fun getGitDiff(workspacePath: String): String {
        if (workspacePath.isBlank()) return "workspacePath required"
        return withContext(Dispatchers.IO) {
            runCatching {
                val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
                val repo = git.repository
                val baos = java.io.ByteArrayOutputStream()
                val formatter = org.eclipse.jgit.diff.DiffFormatter(baos)
                formatter.setRepository(repo)
                val stagedDiff = git.diff().setCached(true).call()
                formatter.format(stagedDiff)
                val unstagedDiff = git.diff().call()
                formatter.format(unstagedDiff)
                formatter.close()
                baos.toString(Charsets.UTF_8.name()).ifEmpty { "no changes" }
            }.getOrElse { "error: ${it.message}" }
        }
    }

    suspend fun gitCommit(workspacePath: String, message: String, all: Boolean): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val commit = git.commit()
            commit.setMessage(message)
            if (all) commit.setAll(true)
            val rev = commit.call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            "committed ${rev.name.take(7)}: $message"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitCheckout(workspacePath: String, target: String, createNew: Boolean = false): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val checkout = git.checkout()
            if (createNew) {
                checkout.setCreateBranch(true).setName(target).call()
                "created and checked out new branch: $target"
            } else {
                checkout.setName(target).call()
                "checked out $target"
            }
            lastStatus = null
            invalidateRepoCache(workspacePath)
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitLog(workspacePath: String, maxCount: Int = 20): JsonArray = withContext(Dispatchers.IO) {
        val result = JsonArray()
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext result
            val commits = git.log().setMaxCount(maxCount).call()
            commits.forEach { rev ->
                val person = rev.authorIdent
                result.add(JsonObject().apply {
                    addProperty("hash", rev.name.take(7))
                    addProperty("fullHash", rev.name)
                    addProperty("author", person.name ?: "unknown")
                    addProperty("email", person.emailAddress ?: "")
                    addProperty("date", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US).format(java.util.Date(rev.commitTime * 1000L)))
                    addProperty("message", rev.shortMessage)
                })
            }
        }
        result
    }

    suspend fun listGitBranches(workspacePath: String): JsonObject = withContext(Dispatchers.IO) {
        val result = JsonObject()
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext result.apply { addProperty("error", "not a git repository") }
            val repo = git.repository
            result.addProperty("current", repo.branch ?: "HEAD")
            val local = JsonArray()
            val remote = JsonArray()
            git.branchList().call().forEach { ref ->
                local.add(JsonObject().apply {
                    addProperty("name", repo.shorten(ref.name))
                    addProperty("fullName", ref.name)
                })
            }
            runCatching {
                git.branchList().setListMode(ListBranchCommand.ListMode.REMOTE).call().forEach { ref ->
                    remote.add(JsonObject().apply {
                        addProperty("name", repo.shorten(ref.name))
                        addProperty("fullName", ref.name)
                    })
                }
            }
            result.add("local", local)
            result.add("remote", remote)
        }.onFailure { result.addProperty("error", it.message ?: "git error") }
        result
    }

    suspend fun gitPull(workspacePath: String, rebase: Boolean = false): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val pull = git.pull().setRemote(Constants.DEFAULT_REMOTE_NAME).setCredentialsProvider(creds())
            if (rebase) pull.setRebase(true)
            val result = pull.call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            if (result.isSuccessful) "pull successful"
            else "pull failed: ${result.mergeResult?.mergeStatus ?: "unknown"}"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitPush(workspacePath: String, force: Boolean = false): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val push = git.push().setRemote(Constants.DEFAULT_REMOTE_NAME).setCredentialsProvider(creds())
            if (force) push.setForce(true)
            val results = push.call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            results.firstOrNull()?.let { r ->
                val updates = r.remoteUpdates.filter { it.status != org.eclipse.jgit.transport.RemoteRefUpdate.Status.OK }
                if (updates.isEmpty()) "push successful"
                else "push issues: ${updates.joinToString("; ") { "${it.remoteName}: ${it.status}" }}"
            } ?: "push completed"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitFetch(workspacePath: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            git.fetch().setRemote(Constants.DEFAULT_REMOTE_NAME).setCredentialsProvider(creds()).call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            "fetch completed"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitCreateBranch(workspacePath: String, branchName: String, startPoint: String? = null): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val cb = git.branchCreate().setName(branchName)
            if (!startPoint.isNullOrBlank()) cb.setStartPoint(startPoint)
            cb.call()
            "created branch: $branchName"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitStash(workspacePath: String, message: String? = null): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val stash = git.stashCreate()
            if (!message.isNullOrBlank()) stash.setReflogMessage(message)
            val ref = stash.call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            if (ref != null) "stashed as ${ref.name.take(7)}"
            else "nothing to stash (clean working tree)"
        }.getOrElse { "error: ${it.message}" }
    }

    suspend fun gitStashPop(workspacePath: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val git = getRepo(workspacePath) ?: return@withContext "not a git repository"
            val ref = git.stashDrop().call()
            lastStatus = null
            invalidateRepoCache(workspacePath)
            if (ref != null) "stash pop applied: ${ref.name.take(7)}"
            else "no stashes to pop"
        }.getOrElse { "error: ${it.message}" }
    } 
}