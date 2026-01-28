package com.rk.git

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.DetachedHeadException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.SubmoduleConfig.FetchRecurseSubmodulesMode
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

class GitViewModel : ViewModel() {
    private var currentRoot = mutableStateOf<File?>(null)
    var currentBranch by mutableStateOf<String>("")

    var isLoading by mutableStateOf(false)

    fun loadRepository(root: String) {
        currentRoot.value = File(root)
        currentBranch = Git.open(currentRoot.value).repository.branch
    }

    fun getBranchList(): List<String> {
        return Git.open(currentRoot.value).use { git ->
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
    }

    private fun Git.currentHead(): String {
        return try {
            repository.branch
        } catch (e: DetachedHeadException) {
            val fullCommitId = repository.fullBranch
            if (fullCommitId != null && fullCommitId.length >= 7) {
                fullCommitId.substring(0, 7)
            } else {
                fullCommitId.toString()
            }
        }
    }

    fun cloneRepository(repoURL: String, repoBranch: String, targetDir: File, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            var done = false
            withContext(Dispatchers.IO) {
                try {
                    Git.cloneRepository()
                        .setURI(repoURL)
                        .setBranch("refs/heads/$repoBranch")
                        .setDirectory(targetDir)
                        .setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(Settings.git_username, Settings.git_password)
                        )
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
                } catch (e: InvalidRemoteException) {
                    toast(strings.invalid_repo_url)
                } catch (e: Exception) {
                    toast(e.message)
                } finally {
                    onComplete(done)
                }
            }
        }
    }

    fun checkoutBranch(branchName: String) {
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
                withContext(Dispatchers.Main) { isLoading = false }
                toast(strings.action_done)
            }
        }
    }

    fun pullRepository() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
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
                withContext(Dispatchers.Main) { isLoading = false }
                toast(strings.action_done)
            }
        }
    }

    fun fetchRepository() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isLoading = true }
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
                withContext(Dispatchers.Main) { isLoading = false }
                toast(strings.action_done)
            }
        }
    }

    companion object {
        private const val GIT_ORIGIN = "origin"
    }
}
