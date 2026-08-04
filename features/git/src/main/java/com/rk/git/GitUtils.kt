package com.rk.git

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rk.theme.gitAdded
import com.rk.theme.gitConflicted
import com.rk.theme.gitDeleted
import com.rk.theme.gitModified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import java.io.File

suspend fun findGitRoot(path: String): String? =
    withContext(Dispatchers.IO) {
        runCatching {
            val startDir = File(path).let { if (it.isDirectory) it else it.parentFile }
            FileRepositoryBuilder()
                .findGitDir(startDir)
                .takeIf { it.gitDir != null }
                ?.build()
                ?.use { repo ->
                    if (!repo.isBare) {
                        repo.workTree?.canonicalPath
                    } else {
                        null
                    }
                }
        }
            .getOrNull()
    }

/** Get the text color for a given [ChangeType] */
@Composable
fun GitChange.getColor(): Color =
    when (type) {
        ChangeType.ADDED,
        ChangeType.UNTRACKED -> MaterialTheme.colorScheme.gitAdded

        ChangeType.DELETED -> MaterialTheme.colorScheme.gitDeleted
        ChangeType.CONFLICTING -> MaterialTheme.colorScheme.gitConflicted
        ChangeType.MODIFIED -> MaterialTheme.colorScheme.gitModified
        ChangeType.RENAMED -> MaterialTheme.colorScheme.gitModified
    }
