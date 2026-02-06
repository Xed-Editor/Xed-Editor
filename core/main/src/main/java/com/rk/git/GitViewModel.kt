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
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class GitViewModel : ViewModel() {
    var currentRoot = mutableStateOf<File?>(null)
    var currentBranch by mutableStateOf("")
    var changes = mutableStateMapOf<String, List<GitChange>>()
    var commitMessages = mutableStateMapOf<String, String>()
    var amends = mutableStateMapOf<String, Boolean>()

    var isLoading by mutableStateOf(false)

    fun loadRepository(root: String) {
        try {
            currentRoot.value = File(root)
            currentBranch = Git.open(currentRoot.value).currentHead()
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
        changes[currentRoot.value!!.absolutePath] =
            changes[currentRoot.value!!.absolutePath]!!.map {
                if (it.path == change.path) it.copy(isChecked = !it.isChecked) else it
            }
    }

    fun addChange(change: GitChange) {
        changes[currentRoot.value!!.absolutePath] =
            changes[currentRoot.value!!.absolutePath]!!.map {
                if (it.path == change.path) it.copy(isChecked = true) else it
            }
    }

    fun removeChange(change: GitChange) {
        changes[currentRoot.value!!.absolutePath] =
            changes[currentRoot.value!!.absolutePath]!!.map {
                if (it.path == change.path) it.copy(isChecked = false) else it
            }
    }

    fun changeCommitMessage(message: String) {
        commitMessages[currentRoot.value!!.absolutePath] = message
    }

    fun toggleAmend(amend: Boolean) {
        amends[currentRoot.value!!.absolutePath] = amend
    }

    fun getChangeType(path: String): ChangeType? {
        changes.forEach { (gitRoot, changes) ->
            if (path.startsWith(gitRoot)) {
                return changes.find { change -> change.absolutePath == path }?.type
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
                        .setBranch("refs/heads/$repoBranch")
                        .setDirectory(targetDir)
                        .setCloneSubmodules(Settings.git_submodules)
                        .setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                        )
                        .setProgressMonitor(progressCoordinator)
                        .call()
                    done = true
                } catch (e: TransportException) {
                    if (
                        e.message?.contains("Auth", true) == true ||
                            e.message?.contains("401") == true ||
                            e.message?.contains("403") == true
                    ) {
                        toast(strings.git_auth_error)
                    } else {
                        toast(e.message)
                    }
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
            try {
                Git.open(currentRoot.value).use { git ->
                    if (branchName.startsWith("$GIT_ORIGIN/")) {
                        val localBranchName = branchName.removePrefix("$GIT_ORIGIN/")
                        val existingBranches = git.branchList().call().map { it.name }
                        if ("refs/heads/$localBranchName" !in existingBranches) {
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
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    toast(strings.checkout_complete)
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun pull(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val pullResult =
                        git.pull()
                            .setRemote(GIT_ORIGIN)
                            .setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                            )
                            .call()
                    if (!pullResult.isSuccessful) {
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
                if (
                    e.message?.contains("Auth", true) == true ||
                        e.message?.contains("401") == true ||
                        e.message?.contains("403") == true
                ) {
                    toast(strings.git_auth_error)
                } else {
                    toast(e.message)
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    toast(strings.pull_complete)
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
                if (
                    e.message?.contains("Auth", true) == true ||
                        e.message?.contains("401") == true ||
                        e.message?.contains("403") == true
                ) {
                    toast(strings.git_auth_error)
                } else {
                    toast(e.message)
                }
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

            val gitRoot = findGitRoot(root)
            if (gitRoot != null) {
                syncChanges(File(gitRoot)).join()
            }
        }
    }

    fun syncChanges(root: File): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            if (!InbuiltFeatures.git.state.value) return@launch

            withContext(Dispatchers.Main) { isLoading = true }
            try {
                val newChanges = mutableListOf<GitChange>()
                Git.open(root).use { git ->
                    val status = git.status().call()
                    fun fullPath(relativePath: String) = File(root, relativePath).absoluteFile
                    newChanges.addAll(status.added.map { GitChange(it, fullPath(it).absolutePath, ChangeType.ADDED) })
                    newChanges.addAll(
                        status.changed.map { GitChange(it, fullPath(it).absolutePath, ChangeType.MODIFIED) }
                    )
                    newChanges.addAll(
                        status.modified.map { GitChange(it, fullPath(it).absolutePath, ChangeType.MODIFIED) }
                    )
                    newChanges.addAll(
                        status.removed.map { GitChange(it, fullPath(it).absolutePath, ChangeType.DELETED) }
                    )
                    newChanges.addAll(
                        status.missing.map { GitChange(it, fullPath(it).absolutePath, ChangeType.DELETED) }
                    )
                    newChanges.addAll(
                        status.untracked.map { GitChange(it, fullPath(it).absolutePath, ChangeType.UNTRACKED) }
                    )
                    newChanges.addAll(
                        status.conflicting.map { GitChange(it, fullPath(it).absolutePath, ChangeType.CONFLICTING) }
                    )
                }
                val gitRoot = root.absolutePath
                val oldChanges = changes[gitRoot]
                val mergedChanges =
                    if (oldChanges != null) {
                        val oldMap = oldChanges.associateBy { it.path }
                        newChanges.map { newChange ->
                            oldMap[newChange.path]?.let { newChange.copy(isChecked = it.isChecked) } ?: newChange
                        }
                    } else {
                        newChanges
                    }
                changes[gitRoot] = mergedChanges
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { isLoading = false }
            }
        }
    }

    fun commit(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    changes[currentRoot.value!!.absolutePath]!!
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
                        .setMessage(commitMessages[currentRoot.value!!.absolutePath])
                        .setAmend(amends[currentRoot.value!!.absolutePath]!!)
                        .call()
                    toast(strings.commit_complete)
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun getCommitCount(): Int {
        try {
            Git.open(currentRoot.value).use { git ->
                val repo = git.repository
                val branch = repo.branch
                val localRef = repo.findRef("refs/heads/$branch")
                val remoteRef = repo.findRef("refs/remotes/$GIT_ORIGIN/$branch")

                RevWalk(repo).use { walk ->
                    val localCommit = walk.parseCommit(localRef!!.objectId)
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
                if (
                    e.message?.contains("Auth", true) == true ||
                        e.message?.contains("401") == true ||
                        e.message?.contains("403") == true
                ) {
                    toast(strings.git_auth_error)
                } else {
                    toast(e.message)
                }
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
            try {
                Git.open(currentRoot.value).use { git ->
                    if (branchBase.startsWith("$GIT_ORIGIN/")) {
                        git.checkout().setName(branchName).setStartPoint(branchBase).setCreateBranch(true).call()
                    } else {
                        git.checkout()
                            .setName(branchName)
                            .setStartPoint("refs/heads/$branchBase")
                            .setCreateBranch(true)
                            .call()
                    }
                    toast(strings.checkout_complete)
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    currentBranch = Git.open(currentRoot.value).currentHead()
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    companion object {
        private const val GIT_ORIGIN = "origin"
    }
}
