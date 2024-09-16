package com.rk.xededitor.git

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File

object git {
  
  enum class RESULT {
    OK, ERROR, USER_ERROR
  }
  
  @OptIn(DelicateCoroutinesApi::class)
  fun findGitRoot(file: File, projectRoot: File, onComplete: (File?) -> Unit) {
    GlobalScope.launch(Dispatchers.IO) {
      var currentFile: File? = file
      while (currentFile?.parentFile?.absolutePath != projectRoot.absolutePath) {
        if (File(currentFile?.parentFile, ".git").exists()) {
          onComplete(currentFile?.parentFile)
          return@launch
        }
        currentFile = currentFile?.parentFile
      }
      onComplete(null)
    }
  }
  
  @OptIn(DelicateCoroutinesApi::class)
  @JvmOverloads
  fun clone(
    repoUrl: String,
    branch: String = "main",
    destinationDir: File,
    username: String? = null,
    password: String? = null,
    onComplete: (RESULT, Exception?) -> Unit
  ) {
    if (repoUrl.isEmpty()) {
      onComplete.invoke(RESULT.USER_ERROR, Exception("repoUrl cannot be null or empty"))
      return
    }
    
    GlobalScope.launch(Dispatchers.IO) {
      val repoName = repoUrl.substringAfterLast("/").removeSuffix(".git")
      if (repoName.isEmpty()) {
        onComplete.invoke(RESULT.USER_ERROR, java.lang.Exception("Invalid repoUrl"))
        return@launch
      }
      try {
        with(Git.cloneRepository()) {
          setURI(repoUrl)
          setDirectory(destinationDir)
          setBranch(branch)
          if (username.isNullOrEmpty() and password.isNullOrEmpty()) {
            UsernamePasswordCredentialsProvider(username, password)
          }
          call()
        }
        onComplete.invoke(RESULT.OK, null)
      } catch (e: Exception) {
        e.printStackTrace()
        onComplete.invoke(RESULT.ERROR, e)
      }
    }
  }
  
  @OptIn(DelicateCoroutinesApi::class)
  fun pull(
    gitRoot: File,
    username: String,
    password: String,
    onComplete: (RESULT, Exception?) -> Unit
  ) {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        val git = Git.open(gitRoot)
        git.pull().setCredentialsProvider(
          UsernamePasswordCredentialsProvider(
            username, password
          )
        ).call()
        onComplete(RESULT.OK, null)
      } catch (e: Exception) {
        onComplete(RESULT.ERROR, e)
      }
    }
  }
  
}