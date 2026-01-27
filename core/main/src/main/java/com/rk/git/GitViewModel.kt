package com.rk.git

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.utils.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.TransportException
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

class GitViewModel : ViewModel() {
    private var currentRoot = mutableStateOf<File?>(null)

    fun setCurrentRoot(root: String) {
        currentRoot.value = File(root)
    }

    fun getCurrentRoot(): String {
        return currentRoot.value?.getAbsolutePath() ?: "fuck"
    }

    fun cloneRepository(
        repoURL: String,
        repoBranch: String,
        targetDir: File,
        onComplete: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            var done = false
            withContext(Dispatchers.IO) {
                try {
                    Git.cloneRepository()
                        .setURI(repoURL)
                        .setBranch("refs/heads/$repoBranch")
                        .setDirectory(targetDir)
                        .setCredentialsProvider(
                            UsernamePasswordCredentialsProvider(
                                Settings.git_username,
                                Settings.git_password
                            )
                        )
                        .call()
                    done = true
                } catch (e: TransportException) {
                    if (e.message?.contains("Auth", true) == true ||
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
}