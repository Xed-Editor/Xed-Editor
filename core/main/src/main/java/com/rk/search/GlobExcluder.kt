package com.rk.search

import java.nio.file.FileSystems
import java.nio.file.Paths

class GlobExcluder(globsText: String) {
    private val matchers =
        globsText
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { FileSystems.getDefault().getPathMatcher("glob:$it") }
            .toList()

    fun isExcluded(absolutePath: String): Boolean {
        val path = Paths.get(absolutePath)
        return matchers.any { it.matches(path) }
    }
}
