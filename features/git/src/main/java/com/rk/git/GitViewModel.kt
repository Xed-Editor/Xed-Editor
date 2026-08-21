package com.rk.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.DefaultScope
import com.rk.events.Events
import com.rk.feature.FeatureRegistry
import com.rk.file.FileWrapper
import com.rk.git.GitViewModel.Companion.LINE_DIFF_DEBOUNCE_MS
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.DetachedHeadException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.diff.DiffAlgorithm
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.diff.RawTextComparator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.revplot.PlotCommitList
import org.eclipse.jgit.revplot.PlotLane
import org.eclipse.jgit.revplot.PlotWalk
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.treewalk.filter.PathFilter
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

data class GitCommit(
    val hash: String,
    val author: String,
    val date: Long,
    val message: String,
    val parentHashes: List<String>,
    val lane: Int,
)

enum class LineDiffType {
    ADDED,
    MODIFIED,
    DELETED,
}

class GitViewModel : ViewModel() {
    private val _currentRoot = MutableStateFlow<File?>(null)
    val currentRoot = _currentRoot.asStateFlow()

    private val _currentBranch = MutableStateFlow("")
    val currentBranch = _currentBranch.asStateFlow()

    private val _changes = MutableStateFlow<Map<String, List<GitChange>>>(emptyMap())
    val changes = _changes.asStateFlow()

    private val _commitMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val commitMessages = _commitMessages.asStateFlow()

    private val _amends = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val amends = _amends.asStateFlow()

    private val _commitHistory = MutableStateFlow<List<GitCommit>?>(null)
    val commitHistory = _commitHistory.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _aheadCount = MutableStateFlow(0)
    val aheadCount = _aheadCount.asStateFlow()

    private val _behindCount = MutableStateFlow(0)
    val behindCount = _behindCount.asStateFlow()

    private val _fileLineDiffs = MutableStateFlow<Map<String, Map<Int, LineDiffType>>>(emptyMap())
    val fileLineDiffs: Map<String, Map<Int, LineDiffType>>
        get() = _fileLineDiffs.value

    private val lineDiffJobs = mutableMapOf<String, Job>()

    fun loadRepository(root: String) {
        try {
            disposeRepository()

            val newRoot = File(root)
            _currentRoot.value = newRoot
            _currentBranch.value = Git.open(newRoot).use { it.currentHead() }
            syncChanges(newRoot)
            _commitHistory.value = null
            if (!_amends.value.containsKey(root)) {
                _amends.update { it + (root to false) }
            }
            if (!_commitMessages.value.containsKey(root)) {
                _commitMessages.update { it + (root to "") }
            }
        } catch (e: Exception) {
            toast(e.message)
        }
    }

    fun disposeRepository() {
        _currentRoot.value = null
        _currentBranch.value = ""
        _changes.value = emptyMap()
        _commitHistory.value = null
    }

    fun getBranchList(): List<String> {
        return try {
            Git.open(_currentRoot.value).use { git ->
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
        _changes.update { map ->
            val root = _currentRoot.value?.absolutePath ?: return@update map
            val list = map[root] ?: return@update map
            map + (root to list.map { if (it.path == change.path) it.copy(isChecked = !it.isChecked) else it })
        }
    }

    fun addChange(change: GitChange) {
        _changes.update { map ->
            val root = _currentRoot.value?.absolutePath ?: return@update map
            val list = map[root] ?: return@update map
            map + (root to list.map { if (it.path == change.path) it.copy(isChecked = true) else it })
        }
    }

    fun removeChange(change: GitChange) {
        _changes.update { map ->
            val root = _currentRoot.value?.absolutePath ?: return@update map
            val list = map[root] ?: return@update map
            map + (root to list.map { if (it.path == change.path) it.copy(isChecked = false) else it })
        }
    }

    fun changeCommitMessage(message: String) {
        _commitMessages.update { map ->
            val root = _currentRoot.value?.absolutePath ?: return@update map
            map + (root to message)
        }
    }

    fun toggleAmend(amend: Boolean) {
        _amends.update { map ->
            val root = _currentRoot.value?.absolutePath ?: return@update map
            map + (root to amend)
        }
    }

    fun getChangeType(path: String): ChangeType? {
        return getChangeForPath(path)?.type
    }

    private fun getChangeForPath(path: String): GitChange? {
        return _changes.value.values.flatten().find { change ->
            change.absolutePath == path
        }
    }

    private fun getChangeAndRootForPath(path: String): Pair<String, GitChange>? {
        _changes.value.forEach { (gitRoot, changes) ->
            val change = changes.find { it.absolutePath == path }
            if (change != null) {
                return gitRoot to change
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
                    DefaultScope.launch {
                        Events.publish(
                            GitEvent.RepositoryCloned(
                                repoURL,
                                repoBranch,
                                FileWrapper(targetDir),
                            )
                        )
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

    fun initRepository(root: File, onInit: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                Git.init().setDirectory(root).call()
                toast(strings.git_init_success)
                Events.publish(GitEvent.RepositoryInitialized(FileWrapper(root)))
                onInit()
            } catch (e: Exception) {
                toast(strings.git_init_error.getFilledString(e.message ?: strings.unknown_error))
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun checkout(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                    withContext(Dispatchers.Main) { _currentBranch.value = git.repository.branch }
                }
                loadHistory()
                Events.publish(
                    GitEvent.BranchCheckedOut(
                        root = FileWrapper(currentRoot.value!!),
                        name = branchName,
                    )
                )
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    toast(strings.checkout_complete)
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun pull(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                loadHistory()
                Events.publish(
                    GitEvent.PullCompleted(
                        root = FileWrapper(currentRoot.value!!),
                        remote = GIT_ORIGIN,
                        branch = currentBranch.value,
                    )
                )
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
                    _isLoading.value = false
                    toast(strings.pull_complete)
                }
            }
        }
    }

    fun fetch() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                loadHistory()
                Events.publish(
                    GitEvent.FetchCompleted(
                        root = FileWrapper(currentRoot.value!!),
                        remote = GIT_ORIGIN,
                        branch = currentBranch.value,
                    )
                )
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
                    _isLoading.value = false
                    toast(strings.fetch_complete)
                }
            }
        }
    }

    fun syncChanges(root: String): Job {
        return viewModelScope.launch {
            if (!FeatureRegistry.isEnabled("enable_git")) return@launch

            val gitRoot = currentRoot.value ?: return@launch
            if (root.startsWith(gitRoot.absolutePath)) {
                syncChanges(gitRoot)
            }
        }
    }

    fun syncChanges(root: File): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            if (!FeatureRegistry.isEnabled("enable_git")) return@launch

            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                val oldChanges = _changes.value[gitRoot]
                val mergedChanges =
                    if (oldChanges != null) {
                        val oldMap = oldChanges.associateBy { it.path }
                        newChanges.map { newChange ->
                            oldMap[newChange.path]?.let { newChange.copy(isChecked = it.isChecked) } ?: newChange
                        }
                    } else {
                        newChanges
                    }
                _changes.update { map -> map + (gitRoot to mergedChanges) }

                updateAheadBehindCounts()
                viewModelScope.launch {
                    Events.publish(GitEvent.WorkingTreeUpdated(FileWrapper(root), mergedChanges))
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun commit(): Job {
        return viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                val currentRoot = currentRoot.value!!
                val message = _commitMessages.value[currentRoot.absolutePath]
                val amend = _amends.value[currentRoot.absolutePath] ?: false

                Git.open(currentRoot).use { git ->
                    (_changes.value[currentRoot.absolutePath] ?: emptyList())
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
                        .setMessage(message)
                        .setAmend(amend)
                        .call()
                    toast(strings.commit_complete)
                }
                if (amend) {
                    Events.publish(
                        GitEvent.CommitAmended(
                            root = FileWrapper(currentRoot),
                            message = message.orEmpty(),
                        )
                    )
                } else {
                    Events.publish(
                        GitEvent.CommitCreated(
                            root = FileWrapper(currentRoot),
                            message = message.orEmpty(),
                        )
                    )
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun push(force: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                updateAheadBehindCounts()
                Events.publish(
                    GitEvent.PushCompleted(
                        root = FileWrapper(currentRoot.value!!),
                        remote = GIT_ORIGIN,
                        branch = currentBranch.value,
                        force = force,
                    )
                )
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
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun discard(change: GitChange) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                val root = currentRoot.value
                Git.open(root).use { git ->
                    when (change.type) {
                        ChangeType.MODIFIED,
                        ChangeType.DELETED,
                        ChangeType.RENAMED,
                        ChangeType.CONFLICTING -> {
                            git.checkout().addPath(change.path).call()
                        }
                        ChangeType.ADDED -> {
                            git.rm().addFilepattern(change.path).call()
                        }
                        ChangeType.UNTRACKED -> {
                            File(change.absolutePath).delete()
                        }
                    }
                }
            } catch (e: Exception) {
                toast(strings.discard_failed.getFilledString(e.message ?: ""))
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun getDiff(change: GitChange, commit: GitCommit? = null, onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                val root = currentRoot.value
                Git.open(root).use { git ->
                    val repo = git.repository

                    ByteArrayOutputStream().use { out ->
                        DiffFormatter(out).use { formatter ->
                            formatter.setRepository(repo)
                            formatter.pathFilter = PathFilter.create(change.path)

                            val (oldTree, newTree) =
                                if (commit != null) {
                                    getCommitDiffTrees(repo, commit)
                                } else {
                                    getWorkingDiffTrees(repo)
                                }

                            formatter.scan(oldTree, newTree).forEach(formatter::format)

                            withContext(Dispatchers.Main) {
                                onResult(out.toString().ifBlank { strings.no_changes.getString() })
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    private fun getWorkingDiffTrees(repo: Repository): Pair<AbstractTreeIterator, FileTreeIterator> {
        val headId = repo.resolve(Constants.HEAD + "^{" + Constants.TYPE_TREE + "}")

        val oldTree =
            if (headId != null) {
                CanonicalTreeParser().apply {
                    reset(repo.newObjectReader(), headId)
                }
            } else {
                EmptyTreeIterator()
            }

        val newTree = FileTreeIterator(repo)

        return oldTree to newTree
    }

    private fun getCommitDiffTrees(
        repo: Repository,
        commit: GitCommit,
    ): Pair<AbstractTreeIterator, CanonicalTreeParser> =
        RevWalk(repo).use { revWalk ->
            val childCommit = revWalk.parseCommit(repo.resolve(commit.hash))

            val old =
                if (childCommit.parentCount > 0) {
                    val parentCommit = revWalk.parseCommit(childCommit.getParent(0).id)
                    CanonicalTreeParser().apply {
                        reset(repo.newObjectReader(), parentCommit.tree.id)
                    }
                } else {
                    EmptyTreeIterator()
                }

            val new =
                CanonicalTreeParser().apply {
                    reset(repo.newObjectReader(), childCommit.tree.id)
                }

            old to new
        }

    fun getChangesForCommit(commit: GitCommit, onResult: (List<GitChange>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }

            try {
                val root = currentRoot.value
                Git.open(root).use { git ->
                    val repo = git.repository

                    val (oldTree, newTree) = getCommitDiffTrees(repo, commit)

                    val entries =
                        git.diff().setOldTree(oldTree).setNewTree(newTree).setShowNameAndStatusOnly(true).call()

                    val changes = entries.map { entry ->
                        val (path, type) =
                            when (entry.changeType) {
                                DiffEntry.ChangeType.ADD -> entry.newPath to ChangeType.ADDED
                                DiffEntry.ChangeType.DELETE -> entry.oldPath to ChangeType.DELETED
                                DiffEntry.ChangeType.RENAME -> entry.newPath to ChangeType.RENAMED
                                DiffEntry.ChangeType.COPY -> entry.newPath to ChangeType.ADDED
                                DiffEntry.ChangeType.MODIFY -> entry.newPath to ChangeType.MODIFIED
                            }

                        GitChange(
                            path = path,
                            absolutePath = File(root, path).absolutePath,
                            type = type,
                            isChecked = false,
                        )
                    }

                    withContext(Dispatchers.Main) { onResult(changes) }
                }
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun checkoutNew(branchName: String, branchBase: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
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
                    toast(strings.checkout_complete)
                }
                loadHistory()
                Events.publish(GitEvent.BranchCreated(FileWrapper(currentRoot.value!!), branchName, branchBase))
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _currentBranch.value = Git.open(currentRoot.value).currentHead()
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun deleteBranch(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    git.branchDelete().setBranchNames(branchName).setForce(true).call()
                    toast(strings.delete_complete)
                }
                Events.publish(GitEvent.BranchDeleted(FileWrapper(currentRoot.value!!), branchName))
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun renameBranch(oldName: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    git.branchRename().setOldName(oldName).setNewName(newName).call()
                    toast(strings.rename_complete)
                }
                Events.publish(GitEvent.BranchRenamed(FileWrapper(currentRoot.value!!), oldName, newName))
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    _currentBranch.value = Git.open(currentRoot.value).currentHead()
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun merge(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val result = git.merge().include(git.repository.resolve(branchName)).call()
                    if (result.mergeStatus.isSuccessful) {
                        toast(strings.merge_complete)
                        Events.publish(GitEvent.Merged(FileWrapper(currentRoot.value!!), currentBranch.value, branchName))
                    } else {
                        toast("Merge failed: ${result.mergeStatus}")
                    }
                }
                loadHistory()
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun rebase(branchName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }
            try {
                Git.open(currentRoot.value).use { git ->
                    val result = git.rebase().setUpstream(branchName).call()
                    if (result.status.isSuccessful) {
                        toast(strings.rebase_complete)
                        Events.publish(GitEvent.Rebased(FileWrapper(currentRoot.value!!), currentBranch.value, branchName))
                    } else {
                        toast("Rebase failed: ${result.status}")
                    }
                }
                loadHistory()
            } catch (e: Exception) {
                toast(e.message)
            } finally {
                withContext(Dispatchers.Main) {
                    _isLoading.value = false
                    syncChanges(currentRoot.value!!)
                }
            }
        }
    }

    fun loadHistory() {
        val root = currentRoot.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _isLoading.value = true }

            try {
                withContext(Dispatchers.Main) { _commitHistory.value = null }

                val commits =
                    Git.open(root).use { git ->
                        val repo = git.repository
                        PlotWalk(repo).use { walk ->
                            walk.sort(RevSort.COMMIT_TIME_DESC)
                            walk.sort(RevSort.TOPO)

                            val headId = repo.resolve(Constants.HEAD) ?: return@use emptyList()
                            val headCommit = walk.parseCommit(headId)
                            walk.markStart(headCommit)

                            val plotCommitList = PlotCommitList<PlotLane>()
                            plotCommitList.source(walk)
                            plotCommitList.fillTo(Integer.MAX_VALUE)

                            buildList {
                                plotCommitList.forEach { plotCommit ->
                                    add(
                                        GitCommit(
                                            hash = plotCommit.name,
                                            author = plotCommit.authorIdent.name,
                                            date = plotCommit.commitTime.toLong() * 1000,
                                            message = plotCommit.shortMessage,
                                            parentHashes = plotCommit.parents.map { it.name },
                                            lane = plotCommit.lane?.position ?: 0,
                                        )
                                    )
                                }
                            }
                        }
                    }

                withContext(Dispatchers.Main) { _commitHistory.value = commits }
            } catch (e: Exception) {
                toast(e.message)
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { _isLoading.value = false }
            }
        }
    }

    fun updateAheadBehindCounts() {
        val root = currentRoot.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Git.open(root).use { git ->
                    val repo = git.repository
                    val branch = repo.branch
                    val localRef = repo.findRef(BRANCH_PREFIX + branch)
                    val remoteRef = repo.findRef("$REMOTE_PREFIX$GIT_ORIGIN/$branch")

                    if (localRef == null) {
                        withContext(Dispatchers.Main) {
                            _aheadCount.value = 0
                            _behindCount.value = 0
                        }
                        return@launch
                    }

                    RevWalk(repo).use { walk ->
                        val localCommit = walk.parseCommit(localRef.objectId)
                        val remoteCommit = remoteRef?.let { walk.parseCommit(it.objectId) }

                        val ahead =
                            if (remoteCommit != null) {
                                walk.reset()
                                walk.markStart(localCommit)
                                walk.markUninteresting(remoteCommit)
                                walk.count()
                            } else 0

                        val behind =
                            if (remoteCommit != null) {
                                walk.reset()
                                walk.markStart(remoteCommit)
                                walk.markUninteresting(localCommit)
                                walk.count()
                            } else 0

                        withContext(Dispatchers.Main) {
                            _aheadCount.value = ahead
                            _behindCount.value = behind
                        }
                    }
                }
            } catch (e: Exception) {
                toast(e.message)
                e.printStackTrace()
            }
        }
    }

    /**
     * Schedules a recomputation of the gutter line-diffs for [absolutePath], comparing [bufferText] (i.e. the editor's
     * current, possibly-unsaved content) against the file's blob at HEAD. Calls arriving in quick succession for the
     * same path (e.g. one per keystroke) are coalesced - only the last one within [LINE_DIFF_DEBOUNCE_MS] actually
     * runs.
     *
     * This is the *only* path that populates [fileLineDiffs], and it is only ever invoked for files that are open in an
     * editor - so files with git changes that aren't currently being viewed are never diffed.
     */
    fun requestLineDiffUpdate(absolutePath: String, bufferText: String) {
        if (!FeatureRegistry.isEnabled("enable_git")) return

        lineDiffJobs[absolutePath]?.cancel()
        lineDiffJobs[absolutePath] =
            viewModelScope.launch(Dispatchers.IO) {
                delay(LINE_DIFF_DEBOUNCE_MS.milliseconds)
                try {
                    val (gitRoot, change) =
                        getChangeAndRootForPath(absolutePath)
                            ?: run {
                                withContext(Dispatchers.Main) { _fileLineDiffs.update { it - absolutePath } }
                                return@launch
                            }

                    Git.open(File(gitRoot)).use { git ->
                        val repo = git.repository

                        val relativePath = change.path

                        val oldText = getHeadRawText(repo, relativePath)
                        val newText = RawText(bufferText.toByteArray(Charsets.UTF_8))
                        val diffs = calculateLineDiffs(oldText, newText)

                        withContext(Dispatchers.Main) {
                            if (diffs.isEmpty()) {
                                _fileLineDiffs.update { it - absolutePath }
                            } else {
                                _fileLineDiffs.update { it + (absolutePath to diffs) }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    lineDiffJobs.remove(absolutePath)
                }
            }
    }

    /** Reads the given path's content at HEAD, or an empty text if it doesn't exist there (new/untracked file). */
    private fun getHeadRawText(repo: Repository, relativePath: String): RawText {
        val headId = repo.resolve(Constants.HEAD) ?: return RawText.EMPTY_TEXT
        return RevWalk(repo).use { revWalk ->
            val commit = revWalk.parseCommit(headId)
            TreeWalk.forPath(repo, relativePath, commit.tree)?.use { treeWalk ->
                val blobId = treeWalk.getObjectId(0)
                RawText(repo.open(blobId).bytes)
            } ?: RawText.EMPTY_TEXT
        }
    }

    private fun calculateLineDiffs(oldText: RawText, newText: RawText): Map<Int, LineDiffType> {
        val diffs = mutableMapOf<Int, LineDiffType>()
        val algorithm = DiffAlgorithm.getAlgorithm(DiffAlgorithm.SupportedAlgorithm.HISTOGRAM)
        val edits = algorithm.diff(RawTextComparator.DEFAULT, oldText, newText)

        for (edit in edits) {
            when (edit.type) {
                Edit.Type.INSERT -> for (i in edit.beginB until edit.endB) diffs[i] = LineDiffType.ADDED
                Edit.Type.REPLACE -> for (i in edit.beginB until edit.endB) diffs[i] = LineDiffType.MODIFIED
                Edit.Type.DELETE -> diffs[edit.beginB] = LineDiffType.DELETED
                else -> {}
            }
        }
        return diffs
    }

    companion object {
        private const val BRANCH_PREFIX = Constants.R_HEADS // refs/heads/
        private const val REMOTE_PREFIX = Constants.R_REMOTES // refs/remotes/
        private const val GIT_ORIGIN = Constants.DEFAULT_REMOTE_NAME // origin
        private const val LINE_DIFF_DEBOUNCE_MS = 50L
    }
}
