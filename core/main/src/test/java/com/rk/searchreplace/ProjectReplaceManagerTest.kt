package com.rk.searchreplace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for ProjectReplaceManager's pure functions. Tests for buildSearchRegex and escapeReplacement which don't
 * require Android context.
 */
class ProjectReplaceManagerTest {

    // region buildSearchRegex tests

    @Test
    fun buildSearchRegex_defaultOptions_matchesCaseInsensitive() {
        val options = ProjectReplaceManager.SearchOptions()
        val regex = ProjectReplaceManager.buildSearchRegex("test", options)

        assertTrue(regex.containsMatchIn("test"))
        assertTrue(regex.containsMatchIn("TEST"))
        assertTrue(regex.containsMatchIn("TeSt"))
        assertTrue(regex.containsMatchIn("This is a test string"))
    }

    @Test
    fun buildSearchRegex_caseSensitive_matchesExactCase() {
        val options = ProjectReplaceManager.SearchOptions(caseSensitive = true)
        val regex = ProjectReplaceManager.buildSearchRegex("Test", options)

        assertTrue(regex.containsMatchIn("Test"))
        assertFalse(regex.containsMatchIn("test"))
        assertFalse(regex.containsMatchIn("TEST"))
    }

    @Test
    fun buildSearchRegex_wholeWord_matchesOnlyWholeWords() {
        val options = ProjectReplaceManager.SearchOptions(wholeWord = true)
        val regex = ProjectReplaceManager.buildSearchRegex("test", options)

        assertTrue(regex.containsMatchIn("test"))
        assertTrue(regex.containsMatchIn("this is a test"))
        assertTrue(regex.containsMatchIn("test is here"))
        assertFalse(regex.containsMatchIn("testing"))
        assertFalse(regex.containsMatchIn("attest"))
        assertFalse(regex.containsMatchIn("contest"))
    }

    @Test
    fun buildSearchRegex_useRegex_matchesRegexPattern() {
        val options = ProjectReplaceManager.SearchOptions(useRegex = true)
        val regex = ProjectReplaceManager.buildSearchRegex("t[eo]st", options)

        assertTrue(regex.containsMatchIn("test"))
        assertTrue(regex.containsMatchIn("tost"))
        assertFalse(regex.containsMatchIn("tast"))
        assertFalse(regex.containsMatchIn("tist"))
    }

    @Test
    fun buildSearchRegex_escapesSpecialCharacters_whenNotRegex() {
        val options = ProjectReplaceManager.SearchOptions(useRegex = false)
        val regex = ProjectReplaceManager.buildSearchRegex("a.b", options)

        assertTrue(regex.containsMatchIn("a.b"))
        assertFalse(regex.containsMatchIn("aXb"))
    }

    @Test
    fun buildSearchRegex_regexWithWholeWord_wrapsPatternWithWordBoundary() {
        val options = ProjectReplaceManager.SearchOptions(useRegex = true, wholeWord = true)
        val regex = ProjectReplaceManager.buildSearchRegex("t[eo]st", options)

        assertTrue(regex.containsMatchIn("test"))
        assertTrue(regex.containsMatchIn("tost"))
        assertFalse(regex.containsMatchIn("testing"))
    }

    // endregion

    // region escapeReplacement tests

    @Test
    fun escapeReplacement_regexMode_allowsBackreferences() {
        val result = ProjectReplaceManager.escapeReplacement("$1 and $2", useRegex = true)
        assertEquals("$1 and $2", result)
    }

    @Test
    fun escapeReplacement_nonRegexMode_escapesDollarSign() {
        val result = ProjectReplaceManager.escapeReplacement("$100", useRegex = false)
        // Kotlin's Regex.escapeReplacement escapes $ as \$
        assertTrue(result.contains("\\$") || !result.contains("\$100"))
    }

    @Test
    fun escapeReplacement_nonRegexMode_literalDollarSign() {
        val replacement = ProjectReplaceManager.escapeReplacement("price: $50", useRegex = false)
        // When used in replace, the escaped string should produce literal $
        val input = "item"
        val output = "item".replace(Regex("item"), replacement)
        assertEquals("price: \$50", output)
    }

    @Test
    fun escapeReplacement_regexMode_backreferenceWorks() {
        val replacement = ProjectReplaceManager.escapeReplacement("($1)", useRegex = true)
        val input = "hello world"
        val output = input.replace(Regex("(world)"), replacement)
        assertEquals("hello (world)", output)
    }

    @Test
    fun escapeReplacement_emptyString_returnsEmpty() {
        assertEquals("", ProjectReplaceManager.escapeReplacement("", useRegex = true))
        assertEquals("", ProjectReplaceManager.escapeReplacement("", useRegex = false))
    }

    // endregion
}
