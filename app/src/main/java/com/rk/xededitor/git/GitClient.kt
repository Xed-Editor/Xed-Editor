package com.rk.xededitor.git

import android.content.Context
import com.rk.libcommons.child
import com.rk.xededitor.ui.screens.settings.git.getToken
import com.rk.xededitor.ui.screens.settings.git.loadGitConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

object GitClient {

    // Clone repository
    suspend fun clone(
        context: Context, url: String, outputDir: File, onResult: (Throwable?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val repoName = url.substringAfterLast("/").removeSuffix(".git")
            val config = loadGitConfig(context)
            val username = config.first
            val passwd = getToken(context)
            runCatching {
                Git.cloneRepository().apply {
                    setURI(url)
                    setCloneAllBranches(true)
                    setCloneSubmodules(true)
                    setDirectory(outputDir.child(repoName))
                    if (username != "root" && passwd.isNotEmpty()) {
                        setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(
                                username, passwd
                            )
                        )
                        
                    }
                    call().also {
                        Git.open(outputDir.child(repoName)).use { git ->
                            git.fetch().setCheckFetchedObjects(true).call()
                        }
                    }
                }

            }.onFailure { onResult.invoke(it) }.onSuccess { onResult.invoke(null) }
        }
    }

    // Pull latest changes
    suspend fun pull(context: Context, root: File, onResult: (Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = loadGitConfig(context)
                val username = config.first
                val passwd = getToken(context)
                Git.open(root).use { git ->
                    git.pull().apply {
                        if (username != "root" && passwd.isNotEmpty()) {
                            setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(
                                    username, passwd
                                )
                            )
                            
                        }
                        call()
                    }

                }
            }.onFailure { onResult.invoke(it) }.onSuccess { onResult.invoke(null) }
        }
    }

    // Push local commits
    suspend fun push(context: Context, root: File, onResult: (Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = loadGitConfig(context)
                val username = config.first
                val passwd = getToken(context)
                Git.open(root).use { git ->
                    git.push().apply {
                        if (username != "root" && passwd.isNotEmpty()) {
                            setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(
                                    username, passwd
                                )
                            )
                            
                        }
                    }.call()
                }
            }.onFailure { onResult.invoke(it) }.onSuccess { onResult.invoke(null) }
        }
    }

    // Commit changes
    suspend fun commit(context: Context,root: File, message: String, onResult: (Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                val config = loadGitConfig(context)
                val username = config.first
                val email = config.second
                val passwd = getToken(context)
                Git.open(root).use { git ->
                    git.add().addFilepattern(".").call()
                    git.commit().setMessage(message).apply {
                        setAuthor(username,email)
                        setCommitter(username, email)
                        if (username != "root" && passwd.isNotEmpty()) {
                            setCredentialsProvider(
                                UsernamePasswordCredentialsProvider(
                                    username, passwd
                                )
                            )
                            
                        }
                    }.call()
                }
            }.onFailure { onResult.invoke(it) }.onSuccess { onResult.invoke(null) }
        }
    }


    suspend fun setBranch(context: Context, root: File, name: String, onResult: (Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            runCatching {
                Git.open(root).use { git ->
                    val branches = git.branchList().call().map { it.name }

                    // Check if the branch name already contains the correct prefix
                    val fullBranchName = when {
                        name.startsWith("refs/heads/") -> name // Local branch with correct prefix
                        name.startsWith("refs/remotes/origin/") -> name // Remote branch with correct prefix
                        else -> {
                            // Determine if it's a local or remote branch and apply the appropriate prefix
                            if (branches.any { it.endsWith("/$name") }) {
                                "refs/heads/$name" // Local branch
                            } else {
                                "refs/remotes/origin/$name" // Remote branch
                            }
                        }
                    }

                    // Checkout the branch
                    git.checkout()
                        .setName(fullBranchName)
                        .call()
                }
            }.onFailure { onResult.invoke(it) }
                .onSuccess { onResult.invoke(null) }
        }
    }


    suspend fun getCurrentBranchFull(context: Context, root: File, onResult: suspend (String?, Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                Git.open(root).use { git ->
                    // Fetch the full reference of the current branch
                    git.repository.fullBranch
                }
            }.onFailure { onResult(null, it) }
                .onSuccess { branchName -> onResult(branchName, null) }
        }
    }


    suspend fun getCurrentBranch(context: Context, root: File, onResult: suspend (String?, Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                Git.open(root).use { git ->
                    git.repository.branch
                }
            }.onFailure { onResult(null, it) }
                .onSuccess { branchName -> onResult(branchName, null) }
        }
    }

    suspend fun getAllBranches(context: Context, root: File, onResult: suspend (List<String>?, Throwable?) -> Unit) {
        withContext(Dispatchers.IO) {
            kotlin.runCatching {
                Git.open(root).use { git ->
                    val localBranches = git.branchList().call().map { it.name }
                    val remoteBranches = git.branchList().setListMode(org.eclipse.jgit.api.ListBranchCommand.ListMode.REMOTE).call().map { it.name }
                    localBranches + remoteBranches // Combine local and remote branches
                }
            }.onFailure { onResult(null, it) }
                .onSuccess { branchNames -> onResult(branchNames, null) }
        }
    }




}
