package com.rk.search.code

import com.rk.search.CodeItem
import kotlinx.coroutines.flow.Flow

/** Strategy for searching code content. */
interface CodeSearchStrategy {
    fun search(query: String): Flow<CodeItem>
}
