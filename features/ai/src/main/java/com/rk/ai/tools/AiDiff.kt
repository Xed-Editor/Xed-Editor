package com.rk.ai.tools

object AiDiff {
    private const val MAX_LINES = 2000
    private const val MAX_CHARS = 8000

    fun unified(path: String, oldText: String, newText: String): String {
        if (oldText == newText) return "$path: no changes"

        val a = oldText.lines()
        val b = newText.lines()
        if (a.size > MAX_LINES || b.size > MAX_LINES) {
            return "$path: ${a.size} → ${b.size} lines (too large to display)"
        }

        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] =
                    if (a[i] == b[j]) {
                        lcs[i + 1][j + 1] + 1
                    } else {
                        maxOf(lcs[i + 1][j], lcs[i][j + 1])
                    }
            }
        }

        val out = StringBuilder("--- $path\n+++ $path\n")
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    i++
                    j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    out.append('-').append(a[i]).append('\n')
                    i++
                }
                else -> {
                    out.append('+').append(b[j]).append('\n')
                    j++
                }
            }
        }
        while (i < n) {
            out.append('-').append(a[i]).append('\n')
            i++
        }
        while (j < m) {
            out.append('+').append(b[j]).append('\n')
            j++
        }

        return out.toString().take(MAX_CHARS)
    }
}
