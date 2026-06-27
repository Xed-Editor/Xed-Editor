package com.rk.git

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.storage.file.FileRepositoryBuilder

suspend fun findGitRoot(path: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val startDir = File(path).let { if (it.isDirectory) it else it.parentFile }
            FileRepositoryBuilder().findGitDir(startDir).takeIf { it.gitDir != null }?.build()?.use { repo ->
                if (!repo.isBare) {
                    repo.workTree?.canonicalPath
                } else {
                    null
                }
            }
        }.getOrNull()
    }