package com.rk.search.index

import android.content.Context
import androidx.room.withTransaction
import com.rk.file.FileObject
import com.rk.search.SearchViewModel
import com.rk.search.utils.GlobExcluder
import com.rk.search.utils.SearchUtils
import com.rk.settings.Settings
import com.rk.utils.logDebug
import com.rk.utils.logError
import com.rk.utils.logWarn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** Manages indexing for a single project with proper lifecycle management. */
class ProjectIndexer(
    private val context: Context,
    private val projectRoot: FileObject,
    private val excluder: GlobExcluder,
    private val onIndexingStateChanged: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val viewModelScope: CoroutineScope,
) {
    companion object {
        private const val CODE_BATCH_SIZE = 5_000
        private const val MAX_CHUNK_SIZE = 1_000_000
        private const val MAX_FILE_SIZE_SEARCH = 10_000_000
    }

    private var indexingJob: Job? = null

    /**
     * Starts full indexing of the project. Cancels any previous indexing job first. Index stores ALL files and code (no
     * filtering by file_mask or excluder).
     */
    suspend fun startIndexing() {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val indexedFiles = fileMetaDao.getAll().associateBy { it.path }
                    val pathsToKeep = mutableSetOf<String>()
                    val newCodeLines = mutableListOf<CodeLine>()
                    val newFileMetas = mutableListOf<FileMeta>()

                    indexRecursively(projectRoot, indexedFiles, pathsToKeep, newCodeLines, newFileMetas, codeLineDao)

                    finalizeIndex(
                        database,
                        indexedFiles,
                        pathsToKeep,
                        codeLineDao,
                        fileMetaDao,
                        newCodeLines,
                        newFileMetas,
                    )

                    logDebug("Indexing completed for $projectRoot")
                } catch (e: CancellationException) {
                    logDebug("Indexing cancelled for $projectRoot")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during indexing")
                    onError("Indexing failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }
    }

    /** Incremental sync of a specific file or directory. Only re-indexes changed files under the given path. */
    suspend fun syncFile(file: FileObject) {
        indexingJob?.cancelAndJoin()

        onIndexingStateChanged(true)

        indexingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val database =
                        try {
                            IndexDatabase.getDatabase(context, projectRoot)
                        } catch (e: Exception) {
                            logError(e, "Failed to get index database for sync, attempting recovery")
                            attemptDatabaseRecovery()
                            IndexDatabase.getDatabase(context, projectRoot)
                        }

                    val codeLineDao = database.codeIndexDao()
                    val fileMetaDao = database.fileMetaDao()

                    val allIndexedFiles = fileMetaDao.getAll().associateBy { it.path }

                    // Only consider files under the changed path
                    val relevantIndexedFiles =
                        if (file == projectRoot) {
                            allIndexedFiles
                        } else {
                            allIndexedFiles.filter { it.key.startsWith(file.getAbsolutePath()) }
                        }

                    val pathsToKeep = mutableSetOf<String>()
                    val newCodeLines = mutableListOf<CodeLine>()
                    val newFileMetas = mutableListOf<FileMeta>()

                    if (file.isDirectory()) {
                        indexRecursively(
                            parent = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineResults = newCodeLines,
                            fileMetaResults = newFileMetas,
                            codeLineDao = codeLineDao,
                        )
                    } else {
                        indexFile(
                            file = file,
                            indexedFiles = relevantIndexedFiles,
                            pathsToKeep = pathsToKeep,
                            codeLineResults = newCodeLines,
                            fileMetaResults = newFileMetas,
                            codeLineDao = codeLineDao,
                        )
                    }

                    finalizeIndex(
                        database = database,
                        indexedFiles = relevantIndexedFiles,
                        pathsToKeep = pathsToKeep,
                        codeLineDao = codeLineDao,
                        fileMetaDao = fileMetaDao,
                        newCodeLines = newCodeLines,
                        newFileMetas = newFileMetas,
                    )

                    logDebug("Sync completed for $file")
                } catch (e: CancellationException) {
                    logDebug("Sync cancelled for $file")
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during file sync")
                    onError("Sync failed: ${e.message}")
                } finally {
                    onIndexingStateChanged(false)
                }
            }

        indexingJob?.join()
    }

    /** Cancels any ongoing indexing operation and waits for it to complete. */
    suspend fun cancelIndexing() {
        indexingJob?.cancelAndJoin()
        indexingJob = null
        onIndexingStateChanged(false)
    }

    /** Closes the database and cleans up resources. Does NOT delete the database file. */
    fun closeDatabase() {
        try {
            IndexDatabase.closeInstance(projectRoot)
            logDebug("Closed index database for $projectRoot")
        } catch (e: Exception) {
            logError(e, "Error closing database")
        }
    }

    private suspend fun attemptDatabaseRecovery() {
        return withContext(Dispatchers.IO) {
            try {
                logWarn("Attempting database recovery by deleting corrupt database")
                IndexDatabase.removeDatabase(context, projectRoot)
                onError("Index was corrupted and has been rebuilt. Please try your search again.")
            } catch (e: Exception) {
                logError(e, "Failed to recover database")
            }
        }
    }

    /** Gets current indexing statistics. */
    suspend fun getStats(): SearchViewModel.IndexingStats {
        return withContext(Dispatchers.IO) {
            try {
                val database = IndexDatabase.getDatabase(context, projectRoot)
                val totalFiles = database.fileMetaDao().getCount()
                val databaseSize = IndexDatabase.getDatabaseSize(context, projectRoot)
                SearchViewModel.IndexingStats(totalFiles, databaseSize)
            } catch (e: Exception) {
                logError(e, "Error getting indexing stats")
                SearchViewModel.IndexingStats(0, 0)
            }
        }
    }

    private suspend fun indexRecursively(
        parent: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        isResultHidden: Boolean = false,
    ) {
        try {
            val childFiles = parent.listFiles()

            for (file in childFiles) {
                currentCoroutineContext().ensureActive()
                indexFile(
                    file = file,
                    indexedFiles = indexedFiles,
                    pathsToKeep = pathsToKeep,
                    codeLineResults = codeLineResults,
                    fileMetaResults = fileMetaResults,
                    codeLineDao = codeLineDao,
                    isResultHidden = isResultHidden,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error during recursive indexing")
        }
    }

    private suspend fun indexFile(
        file: FileObject,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineResults: MutableList<CodeLine>,
        fileMetaResults: MutableList<FileMeta>,
        codeLineDao: CodeLineDao,
        isResultHidden: Boolean = false,
    ) {
        try {
            val isHidden = file.getName().startsWith(".") || isResultHidden
            if (isHidden && !Settings.show_hidden_files_search) return

            val path = file.getAbsolutePath()
            val lastModified = file.lastModified() ?: 0L

            if (excluder.isExcluded(path)) return

            val indexedFile = indexedFiles[path]
            val isFileModified =
                indexedFile == null || indexedFile.lastModified != lastModified || indexedFile.size != file.length()

            if (!isFileModified) {
                pathsToKeep += path
                if (!file.isDirectory()) return
            } else {
                fileMetaResults.add(
                    FileMeta(path = path, fileName = file.getName(), lastModified = lastModified, size = file.length())
                )
            }

            if (file.isDirectory()) {
                indexRecursively(
                    parent = file,
                    indexedFiles = indexedFiles,
                    pathsToKeep = pathsToKeep,
                    codeLineResults = codeLineResults,
                    fileMetaResults = fileMetaResults,
                    codeLineDao = codeLineDao,
                    isResultHidden = isHidden,
                )
                return
            }

            if (!SearchUtils.isFileSearchable(file)) return

            val charset = Charset.forName(Settings.encoding)
            file.useInputStream { inputStream ->
                inputStream.bufferedReader(charset).useLines { lineSequence ->
                    lineSequence.forEachIndexed { lineIndex, line ->
                        currentCoroutineContext().ensureActive()

                        val chunks = line.chunked(MAX_CHUNK_SIZE)
                        chunks.forEachIndexed { chunkIndex, chunk ->
                            codeLineResults.add(
                                CodeLine(
                                    content = chunk,
                                    path = path,
                                    lineNumber = lineIndex,
                                    chunkStart = chunkIndex * MAX_CHUNK_SIZE,
                                )
                            )

                            // Flush batch to avoid OOM
                            if (codeLineResults.size > CODE_BATCH_SIZE) {
                                codeLineDao.insertAll(codeLineResults)
                                codeLineResults.clear()
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error indexing file: ${file.getAbsolutePath()}")
        }
    }

    private suspend fun finalizeIndex(
        database: IndexDatabase,
        indexedFiles: Map<String, FileMeta>,
        pathsToKeep: MutableSet<String>,
        codeLineDao: CodeLineDao,
        fileMetaDao: FileMetaDao,
        newCodeLines: MutableList<CodeLine>,
        newFileMetas: MutableList<FileMeta>,
    ) {
        return withContext(Dispatchers.IO) {
            try {
                currentCoroutineContext().ensureActive()

                database.withTransaction {
                    // Delete files that are no longer present or were modified
                    val deletedPaths = indexedFiles.keys - pathsToKeep
                    for (path in deletedPaths) {
                        codeLineDao.deleteByPath(path)
                        fileMetaDao.deleteByPath(path)
                    }

                    // Insert new/updated entries
                    if (newCodeLines.isNotEmpty()) {
                        codeLineDao.insertAll(newCodeLines)
                    }
                    if (newFileMetas.isNotEmpty()) {
                        fileMetaDao.insertAll(newFileMetas)
                    }
                }
            } catch (e: Exception) {
                logError(e, "Error finalizing index")
                throw e
            }
        }
    }
}
