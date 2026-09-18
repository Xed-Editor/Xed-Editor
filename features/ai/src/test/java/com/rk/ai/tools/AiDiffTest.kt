package com.rk.ai.tools

import org.junit.Assert.assertTrue
import org.junit.Test

class AiDiffTest {
    @Test
    fun reportsNoChangesForIdenticalText() {
        assertTrue(AiDiff.unified("a.kt", "one\ntwo\n", "one\ntwo\n").contains("no changes"))
    }

    @Test
    fun marksReplacedLineAsRemovalAndAddition() {
        val diff = AiDiff.unified("a.kt", "one\ntwo\n", "one\nthree\n")
        assertTrue(diff.contains("-two"))
        assertTrue(diff.contains("+three"))
    }

    @Test
    fun marksAppendedLineAsAddition() {
        val diff = AiDiff.unified("a.kt", "one\n", "one\ntwo\n")
        assertTrue(diff.contains("+two"))
    }

    @Test
    fun marksDeletedLineAsRemoval() {
        val diff = AiDiff.unified("a.kt", "one\ntwo\n", "one\n")
        assertTrue(diff.contains("-two"))
    }

    @Test
    fun summarisesOversizedFiles() {
        val old = (1..3000).joinToString("\n")
        val new = old + "\nextra"
        assertTrue(AiDiff.unified("big.txt", old, new).contains("too large"))
    }
}
