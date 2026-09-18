package com.rk.ai.tools

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobTest {
    private fun matches(pattern: String, path: String) = FileOpsTools.globToRegex(pattern).matches(path)

    @Test
    fun singleStarStaysWithinOneSegment() {
        assertTrue(matches("*.kt", "Main.kt"))
        assertFalse(matches("*.kt", "src/Main.kt"))
    }

    @Test
    fun doubleStarSpansDirectories() {
        assertTrue(matches("**/*.kt", "Main.kt"))
        assertTrue(matches("**/*.kt", "src/a/Main.kt"))
    }

    @Test
    fun anchoredPatternRespectsLeadingDirectory() {
        assertTrue(matches("src/**/Main.kt", "src/Main.kt"))
        assertTrue(matches("src/**/Main.kt", "src/a/b/Main.kt"))
        assertFalse(matches("src/**/Main.kt", "test/Main.kt"))
    }

    @Test
    fun questionMarkMatchesExactlyOneCharacter() {
        assertTrue(matches("a?.kt", "ab.kt"))
        assertFalse(matches("a?.kt", "abc.kt"))
    }

    @Test
    fun dotsAreEscapedNotRegexWildcards() {
        assertTrue(matches("build.gradle.kts", "build.gradle.kts"))
        assertFalse(matches("build.gradle.kts", "buildXgradleYkts"))
    }
}
