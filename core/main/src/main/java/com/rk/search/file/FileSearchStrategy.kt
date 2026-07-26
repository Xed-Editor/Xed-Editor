package com.rk.search.file

import com.rk.file.FileObject
import com.rk.search.index.FileMeta

/** Strategy for searching file names. */
interface FileSearchStrategy {
    suspend fun search(query: String, projectRoot: FileObject): List<FileMeta>
}
