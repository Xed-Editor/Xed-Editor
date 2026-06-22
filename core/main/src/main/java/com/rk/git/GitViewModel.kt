package com.rk.git

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.app.InbuiltFeatures
import com.rk.utils.findGitRoot
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.DetachedHeadException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private const val SYNC_DEBOUNCE_MS = 500L

private fun isAuthError(e: Exception): Boolean {
    val msg = e.message ?: return false
    return msg.contains("Auth", true) || msg.contains("401") || msg.contains("403")
}

private fun mapStatusChanges(
    status: org.eclipse.jgit.api.Status,
    root: File,
): List<GitChange> {
    fun fullPath(relativePath: String) = File(root, relativePath).absoluteFile
    return buildList {
        status.added.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.ADDED) }
        status.changed.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.MODIFIED) }
        status.modified.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.MODIFIED) }
        status.removed.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.DELETED) }
        status.missing.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.DELETED) }
        status.untracked.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.UNTRACKED) }
        status.conflicting.mapTo(this) { GitChange(it, fullPath(it).absolutePath, ChangeType.CONFLICTING) }
    }
}

class GitViewModel : ViewModel() {
    var currentRoot = mutableStateOf<File?>(null)
    var currentBranch by mutableStateOf("")
    var changes = mutableStateMapOf<String, List<GitChange>>()
    var commitMessages = mutableStateMapOf<String, String>()
    var amends = mutableStateMapOf<String, Boolean>()

    var isLoading by mutableStateOf(false)

    private val syncJobs = ConcurrentHashMap<String, Job>()
    private val gitRootCache = ConcurrentHashMap<String, String>()
    private val syncMutex = Mutex()

    // Git Log state
    var commitLog = mutableStateOf<List<CommitInfo>>(emptyList())
    var isLogLoading by mutableStateOf(false)

    // Git Stash state
    var stashList = mutableStateOf<List<StashEntry>>(emptyList())
    var isStashLoading by mutableStateOf(false)

    fun loadRepository(root: String) {
        try {
            currentRoot.value = File(root)
            currentBranch = Git.open(currentRoot.value).use { it.currentHead() }
            syncChanges(currentRoot.value!!)
            if (!amends.containsKey(root)) {
                amends[root] = false
            }
            if (!commitMessages.containsKey(root)) {
                commitMessages[root] = ""
            }
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    fun getBranchList(): List<String> {
        return try {
            Git.open(currentRoot.value).use { git ->
                val branches = mutableListOf<String>()
                val refs = git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call()
                for (ref in refs) {
                    val name = Repository.shortenRefName(ref.name)
                    branches.add(name)
                }
                val current = git.currentHead()
                if (current !in branches) {
                    branches.add(0, current)
                }
                branches
            }
        } catch (e: Exception) {
            toast(e.message)
            emptyList()
        }
    }

    private fun Git.currentHead(): String {
        return try {
            repository.branch
        } catch (_: DetachedHeadException) {
            val fullCommitId = repository.fullBranch
            if (fullCommitId != null && fullCommitId.length >= 7) {
                fullCommitId.take(7)
            } else {
                fullCommitId.toString()
            }
        }
    }

    fun toggleChange(change: GitChange) {
        val gitRoot = currentRoot.value?.absolutePath ?: return
        changes[gitRoot] =
            changes[gitRoot]?.map {
                if (it.path == change.path) it.copy(isChecked = !it.isChecked) else it
            } ?: emptyList()
    }

    fun addChange(change: GitChange) {
        val gitRoot = currentRoot.value?.absolutePath ?: return
        changes[gitRoot] =
            changes[gitRoot]?.map {
                if (it.path == change.path) it.copy(isChecked = true) else it
            } ?: emptyList()
    }

    fun removeChange(change: GitChange) {
        val gitRoot = currentRoot.value?.absolutePath ?: return
        changes[gitRoot] =
            changes[gitRoot]?.map {
                if (it.path == change.path) it.copy(isChecked = false) else it
            } ?: emptyList()
    }

    fun changeCommitMessage(message: String) {
        val gitRoot = currentRoot.value?.absolutePath ?: return
        commitMessages[gitRoot] = message
    }

    fun toggleAmend(amend: Boolean) {
        val gitRoot = currentRoot.value?.absolutePath ?: return
        amends[gitRoot] = amend
    }

    fun getChangeType(path: String): ChangeType? {
        changes.forEach { (gitRoot, repoChanges) ->
            if (path.startsWith(gitRoot)) {
                return repoChanges.find { change -> change.absolutePath == path }?.type
            }
        }
        return null
    }

    fun cloneRepository(
        repoURL: String,
        repoBranch: String,
        targetDir: File,
        progressCoordinator: ProgressCoordinator,
        onComplete: (Boolean) -> Unit,
    ) {
        viewModelScope.launch {
            var done = false
            withContext(Dispatchers.IO) {
                try {
                    progressCoordinator.showDialog()
                    Git.cloneRepository()
                        .setURI(repoURL)
                        .setBranch(BRANCH_PREFIX + repoBranch)
                        .setDirectory(targetDir)
                        .setCloneSubmodules(Settings.git_submodules)
                        .setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                        )
                        .setProgressMonitor(progressCoordinator)
                        .call()
                    done = true
                } catch (e: TransportException) {
                    if (isAuthError(e)) toast(strings.git_auth_error) else toast(e.message)
                } catch (_: InvalidRemoteException) {
                    toast(strings.invalid_repo_url)
                } catch (e: Exception) {
                    toast(e.message)
                } finally {
                    progressCoordinator.hideDialog()
                    onComplete(done)
                }
            }
        }
    }

    fun checkout(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            var success = false
            try {
                Git.open(currentRoot.value).use { git ->
                    if (branchName.startsWith("$GIT_ORIGIN/")) {
                        val localBranchName = branchName.removePrefix("$GIT_ORIGIN/")
                        val existingBranches = git.branchList().call().map { it.name }
                        if (BRANCH_PREFIX + localBranchName !in existingBranches) {
                            git.checkout()
                                .setCreateBranch(true)
                                .setName(localBranchName)
                                .setStartPoint(branchName)
                                .call()
                        } else {
                            git.checkout().setName(localBranchName).call()
                        }
                    } else {
                        git.checkout().setName(branchName).call()
                    }
                    withContext(Dispatchers.Main) { currentBranch = git.repository.branch }
                    success = true
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (success) {
                        toast(strings.checkout_complete)
                        currentRoot.value?.let { syncChanges(it) }
                    }
                }
            }
        }
    }

    fun pull(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            var success = false
            try {
                Git.open(currentRoot.value).use { git ->
                    val pullResult =
                        git.pull()
                            .setRemote(GIT_ORIGIN)
                            .setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                            )
                            .call()
                    if (pullResult.isSuccessful) {
                        success = true
                    } else {
                        val errorMessage = buildString {
                            pullResult.mergeResult?.let { mergeResult ->
                                append("Merge status: ${mergeResult.mergeStatus}")
                                if (!mergeResult.mergeStatus.isSuccessful) {
                                    append(", Conflicts: ${mergeResult.conflicts?.keys?.joinToString() ?: "none"}")
                                }
                            }
                            pullResult.rebaseResult?.let { rebaseResult ->
                                if (isNotEmpty()) append("; ")
                                append("Rebase status: ${rebaseResult.status}")
                            }
                        }
                        toast(errorMessage)
                    }
                }
            } catch (e: TransportException) {
                if (isAuthError(e)) toast(strings.git_auth_error) else toast(e.message)
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (success) toast(strings.pull_complete)
                }
            }
        }
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    git.fetch()
                        .setRemote(GIT_ORIGIN)
                        .setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                        )
                        .setRecurseSubmodules(
                            if (Settings.git_recursive_submodules) {
                                FetchRecurseSubmodulesMode.YES
                            } else {
                                FetchRecurseSubmodulesMode.ON_DEMAND
                            }
                        )
                        .setCheckFetchedObjects(true)
                        .setRemoveDeletedRefs(true)
                        .call()
                }
            } catch (e: TransportException) {
                if (isAuthError(e)) toast(strings.git_auth_error) else toast(e.message)
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    toast(strings.fetch_complete)
                }
            }
        }
    }

    fun syncChanges(root: String): Job {
        return viewModelScope.launch {
            if (!InbuiltFeatures.git.state.value) return@launch

            val gitRoot = gitRootCache[root] ?: withContext(Dispatchers.IO) { findGitRoot(root) }
            if (gitRoot != null) {
                gitRootCache[root] = gitRoot
                syncChanges(File(gitRoot))
            }
        }
    }

    fun syncChanges(root: File): Job {
        val absolutePath = root.absolutePath
        val existingJob = syncJobs[absolutePath]
        if (existingJob != null && existingJob.isActive) {
            return existingJob
        }

        val job =
            viewModelScope.launch(Dispatchers.IO) {
                if (!InbuiltFeatures.git.state.value) return@launch
                delay(SYNC_DEBOUNCE_MS)

                syncMutex.withLock {
                    withContext(Dispatchers.Main) { isLoading = true }
                    try {
                        val newChanges = mutableListOf<GitChange>()
                        Git.open(root).use { git ->
                            val status = git.status().call()
                            newChanges.addAll(mapStatusChanges(status, root))
                        }
                        val gitRoot = root.absolutePath
                        val oldChanges = changes[gitRoot]
                        val mergedChanges =
                            if (oldChanges != null) {
                                val oldMap = oldChanges.associateBy { it.path }
                                newChanges.map { newChange ->
                                    oldMap[newChange.path]?.let { newChange.copy(isChecked = it.isChecked) }
                                        ?: newChange
                                }
                            } else {
                                newChanges
                            }
                        withContext(Dispatchers.Main) { changes[gitRoot] = mergedChanges }
                    } catch (e: Exception) {
                        toast(e.message)
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                        syncJobs.remove(absolutePath)
                    }
                }
            }
        syncJobs[absolutePath] = job
        return job
    }

    fun commit(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val rootPath = currentRoot.value?.absolutePath ?: return@use
                    (changes[rootPath] ?: return@use)
                        .filter { it.isChecked }
                        .forEach { change ->
                            when (change.type) {
                                ChangeType.ADDED -> git.add().addFilepattern(change.path).call()
                                ChangeType.UNTRACKED -> git.add().addFilepattern(change.path).call()
                                ChangeType.MODIFIED -> git.add().addFilepattern(change.path).call()
                                ChangeType.DELETED -> git.rm().addFilepattern(change.path).call()
                                else -> {}
                            }
                        }
                    git.commit()
                        .setAuthor(Settings.git_name, Settings.git_email)
                        .setCommitter(Settings.git_name, Settings.git_email)
                        .setMessage(commitMessages[rootPath])
                        .setAmend(amends[rootPath] ?: false)
                        .call()
                    toast(strings.commit_complete)
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    currentRoot.value?.let { syncChanges(it) }
                }
            }
        }
    }

    fun getCommitCount(): Int {
        try {
            Git.open(currentRoot.value).use { git ->
                val repo = git.repository
                val branch = repo.branch
                val localRef = repo.findRef(BRANCH_PREFIX + branch)
                val remoteRef = repo.findRef("$REMOTE_PREFIX$GIT_ORIGIN/$branch")

                if (localRef == null) return -1

                RevWalk(repo).use { walk ->
                    val localCommit = walk.parseCommit(localRef.objectId)
                    walk.markStart(localCommit)
                    if (remoteRef != null) {
                        val remoteCommit = walk.parseCommit(remoteRef.objectId)
                        walk.markUninteresting(remoteCommit)
                    }
                    return walk.count()
                }
            }
        } catch (e: Exception) {
            toast(e.message)
            return -1
        }
    }

    fun push(force: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val pushResults =
                        git.push()
                            .setRemote(GIT_ORIGIN)
                            .setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                            )
                            .setForce(force)
                            .call()
                    val errorMessage = buildString {
                        for (result in pushResults) {
                            for (update in result.remoteUpdates) {
                                val ref = update.remoteName
                                val status = update.status
                                if (
                                    status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE
                                ) {
                                    if (isNotEmpty()) append("; ")
                                    append("$ref: $status")
                                    update.message?.let { append(" ($it)") }
                                }
                            }
                        }
                    }
                    if (errorMessage.isNotEmpty()) {
                        toast(errorMessage)
                    } else {
                        toast(strings.push_complete)
                    }
                }
            } catch (e: TransportException) {
                if (isAuthError(e)) toast(strings.git_auth_error) else toast(e.message)
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun checkoutNew(branchName: String, branchBase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            var success = false
            try {
                Git.open(currentRoot.value).use { git ->
                    if (branchBase.startsWith("$GIT_ORIGIN/")) {
                        git.checkout().setName(branchName).setStartPoint(branchBase).setCreateBranch(true).call()
                    } else {
                        git.checkout()
                            .setName(branchName)
                            .setStartPoint(BRANCH_PREFIX + branchBase)
                            .setCreateBranch(true)
                            .call()
                    }
                    success = true
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (success) {
                        toast(strings.checkout_complete)
                        currentRoot.value?.let { root ->
                            viewModelScope.launch(Dispatchers.IO) {
                                val branch = Git.open(root).use { it.currentHead() }
                                withContext(Dispatchers.Main) { currentBranch = branch }
                                syncChanges(root)
                            }
                        }
                    }
                }
            }
        }
    }

    // Git Stash operations
    fun stashChanges(message: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val stashCommand = git.stashCreate()
                    if (!message.isNullOrBlank()) {
                        stashCommand.setRef("refs/stash")
                        stashCommand.setWorkingDirectoryMessage(message)
                    }
                    stashCommand.call()
                    toast("Changes stashed")
                    currentRoot.value?.let { syncChanges(it) }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun popStash(stashIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    // Pop = apply + drop
                    git.stashApply().setStashRef("stash@{$stashIndex}").call()
                    git.stashDrop().setStashRef(stashIndex).call()
                    toast("Stash popped")
                    currentRoot.value?.let { syncChanges(it) }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun applyStash(stashIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    git.stashApply().setStashRef("stash@{$stashIndex}").call()
                    toast("Stash applied")
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun dropStash(stashIndex: Int = 0) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    git.stashDrop().setStashRef(stashIndex).call()
                    toast("Stash dropped")
                    loadStashList()
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun loadStashList() {
        viewModelScope.launch(Dispatchers.IO) {
            isStashLoading = true
            try {
                Git.open(currentRoot.value).use { git ->
                    val gitStashList = git.stashList().call()
                    val entries =
                        gitStashList.mapIndexed { index, revCommit ->
                            StashEntry(
                                index = index,
                                message = revCommit.fullMessage.trim(),
                                author = revCommit.authorIdent.name,
                                date = revCommit.authorIdent.`when`,
                            )
                        }
                    withContext(Dispatchers.Main) { stashList.value = entries }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isStashLoading = false }
            }
        }
    }

    // Git Log operations
    fun loadCommitLog(maxCount: Int = 50) {
        viewModelScope.launch(Dispatchers.IO) {
            isLogLoading = true
            try {
                Git.open(currentRoot.value).use { git ->
                    val log =
                        git.log()
                            .setMaxCount(maxCount)
                            .call()
                    val commits =
                        log.map { revCommit ->
                            CommitInfo(
                                hash = revCommit.id.name.substring(0, 7),
                                fullHash = revCommit.id.name,
                                message = revCommit.shortMessage.trim(),
                                author = revCommit.authorIdent.name,
                                email = revCommit.authorIdent.emailAddress,
                                date = revCommit.authorIdent.`when`,
                                isMerge = revCommit.parentCount > 1,
                            )
                        }
                    withContext(Dispatchers.Main) { commitLog.value = commits }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLogLoading = false }
            }
        }
    }

    fun getFileHistory(filePath: String, maxCount: Int = 20): List<CommitInfo> {
        return try {
            Git.open(currentRoot.value).use { git ->
                git.log()
                    .setMaxCount(maxCount)
                    .addPath(filePath)
                    .call()
                    .map { revCommit ->
                        CommitInfo(
                            hash = revCommit.id.name.substring(0, 7),
                            fullHash = revCommit.id.name,
                            message = revCommit.shortMessage.trim(),
                            author = revCommit.authorIdent.name,
                            email = revCommit.authorIdent.emailAddress,
                            date = revCommit.authorIdent.`when`,
                            isMerge = revCommit.parentCount > 1,
                        )
                    }
            }
        } catch (e: Exception) {
            toast(e.message)
            emptyList()
        }
    }

    fun getDiffForFile(filePath: String): String? {
        return try {
            Git.open(currentRoot.value).use { git ->
                val repository = git.repository
                val out = java.io.ByteArrayOutputStream()
                org.eclipse.jgit.diff.DiffFormatter(out).use { df ->
                    df.setRepository(repository)
                    df.setPathFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath))
                    val head = repository.resolve("HEAD")
                    if (head != null) {
                        val headCommit = repository.parseCommit(head)
                        val headTree = headCommit.tree
                        val headTreeIterator = org.eclipse.jgit.treewalk.CanonicalTreeParser().apply {
                            val reader = repository.newObjectReader()
                            try {
                                reset(reader, headTree.id)
                            } finally {
                                reader.close()
                            }
                        }
                        val workTreeIterator = org.eclipse.jgit.treewalk.FileTreeIterator(repository)
                        val entries = df.scan(headTreeIterator, workTreeIterator)
                        df.format(entries)
                        val diff = out.toString("UTF-8")
                        if (diff.isBlank()) {
                            val file = File(currentRoot.value, filePath)
                            if (file.exists() && file.isFile) {
                                file.readLines().joinToString("\n") { "+ $it" }
                            } else {
                                null
                            }
                        } else {
                            diff
                        }
                    } else {
                        val file = File(currentRoot.value, filePath)
                        if (file.exists() && file.isFile) {
                            file.readLines().joinToString("\n") { "+ $it" }
                        } else {
                            null
                        }
                    }
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getFileDiffContent(filePath: String): String {
        return try {
            Git.open(currentRoot.value).use { git ->
                val head = git.repository.resolve("HEAD")
                val headCommit = git.repository.parseCommit(head)
                val treeWalk = org.eclipse.jgit.treewalk.TreeWalk(git.repository)
                treeWalk.addTree(headCommit.tree)
                treeWalk.setRecursive(true)
                treeWalk.setFilter(org.eclipse.jgit.treewalk.filter.PathFilter.create(filePath))

                if (treeWalk.next()) {
                    val blobId = treeWalk.getObjectId(0)
                    val obj = git.repository.newObjectReader().open(blobId)
                    String(obj.bytes)
                } else {
                    "File not found in HEAD"
                }
            }
        } catch (e: Exception) {
            "Error reading file: ${e.message}"
        }
    }

    companion object {
        private const val BRANCH_PREFIX = Constants.R_HEADS // refs/heads/
        private const val REMOTE_PREFIX = Constants.R_REMOTES // refs/remotes/
        private const val GIT_ORIGIN = Constants.DEFAULT_REMOTE_NAME // origin
    }
}

data class CommitInfo(
    val hash: String,
    val fullHash: String,
    val message: String,
    val author: String,
    val email: String?,
    val date: java.util.Date,
    val isMerge: Boolean,
)

data class StashEntry(
    val index: Int,
    val message: String,
    val author: String,
    val date: java.util.Date,
)
