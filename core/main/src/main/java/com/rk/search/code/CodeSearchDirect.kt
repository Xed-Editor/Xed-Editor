package com.rk.search.code

import android.content.Context
import com.rk.activities.main.MainViewModel
import com.rk.file.FileObject
import com.rk.search.CodeItem
import com.rk.search.utils.GlobExcluder
import com.rk.search.utils.SearchUtils
import com.rk.settings.Settings
import com.rk.utils.logError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import java.nio.charset.Charset

/** Code search using filesystem traversal. No index required. */
class CodeSearchDirect(
    private val context: Context,
    private val projectRoot: FileObject,
    private val mainViewModel: MainViewModel,
    private val fileMaskFilter: (String) -> Boolean,
    private val excluder: GlobExcluder,
    private val ignoreCase: Boolean,
    private val openPaths: Set<String>,
) : CodeSearchStrategy {

    companion object {
        private const val MAX_CHUNK_SIZE = 1_000_000 // 1 MB limit per column
    }

    override fun search(query: String): Flow<CodeItem> = channelFlow {
        withContext(Dispatchers.IO) {
            try {
                searchRecursively(
                    parent = projectRoot,
                    isResultHidden = false,
                    query = query,
                    sendFn = { send(it) },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError(e, "Error during direct code search")
            }
        }
    }

    private suspend fun searchRecursively(
        parent: FileObject,
        isResultHidden: Boolean,
        query: String,
        sendFn: suspend (CodeItem) -> Unit,
    ) {
        try {
            val childFiles = parent.listFiles()

            for (file in childFiles) {
                currentCoroutineContext().ensureActive()

                val path = file.getAbsolutePath()
                if (path in openPaths) continue

                val fileExt = file.getExtension()
                if (file.isFile() && !fileMaskFilter(fileExt)) continue

                if (excluder.isExcluded(path)) continue

                val isHidden = file.getName().startsWith(".") || isResultHidden
                if (isHidden && !Settings.show_hidden_files_search) continue

                if (file.isDirectory()) {
                    searchRecursively(file, isHidden, query, sendFn)
                    continue
                }

                if (!SearchUtils.isFileSearchable(file)) continue
                val charset = Charset.forName(Settings.encoding)

                file.useInputStream { inputStream ->
                    inputStream.bufferedReader(charset).useLines { lineSequence ->
                        lineSequence.forEachIndexed { lineIndex, line ->
                            val chunks = line.chunked(MAX_CHUNK_SIZE)
                            chunks.forEachIndexed { chunkIndex, chunk ->
                                val indices = SearchUtils.findAllIndices(chunk, query, ignoreCase = ignoreCase)
                                for (index in indices) {
                                    currentCoroutineContext().ensureActive()
                                    val absoluteCharIndex = (chunkIndex * MAX_CHUNK_SIZE) + index
                                    currentCoroutineContext().ensureActive()
                                    sendFn(
                                        SearchUtils.createCodeItem(
                                            context = context,
                                            mainViewModel = mainViewModel,
                                            text = chunk,
                                            charIndex = absoluteCharIndex,
                                            query = query,
                                            file = file,
                                            projectRoot = projectRoot,
                                            lineIndex = lineIndex,
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError(e, "Error during recursive file search")
        }
    }
}
