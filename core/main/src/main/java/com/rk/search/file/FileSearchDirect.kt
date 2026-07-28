package com.rk.search.file

import com.rk.file.FileObject
import com.rk.search.index.FileMeta
import com.rk.search.utils.GlobExcluder
import com.rk.settings.Settings
import com.rk.utils.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** File search using filesystem traversal. Slower but doesn't require index. */
class FileSearchDirect(private val excluder: GlobExcluder) : FileSearchStrategy {
    override suspend fun search(query: String, projectRoot: FileObject): List<FileMeta> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<FileMeta>()

            suspend fun searchRecursively(parent: FileObject) {
                try {
                    val childFiles = parent.listFiles()

                    for (file in childFiles) {
                        currentCoroutineContext().ensureActive()

                        val path = file.getAbsolutePath()
                        if (excluder.isExcluded(path)) continue

                        val isHidden = file.getName().startsWith(".")
                        if (isHidden && !Settings.show_hidden_files_search) continue

                        if (file.getName().contains(query, ignoreCase = true)) {
                            results.add(
                                // Last modified and size do not matter here, as they're only used for indexing
                                FileMeta(
                                    path = path,
                                    fileName = file.getName(),
                                    lastModified = 0,
                                    size = 0,
                                )
                            )
                        }

                        if (file.isDirectory()) {
                            searchRecursively(file)
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logError(e, "Error during file search")
                }
            }

            searchRecursively(projectRoot)
            results
        }
}
