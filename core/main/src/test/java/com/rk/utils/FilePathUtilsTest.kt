package com.rk.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePathUtilsTest {

    @get:Rule @JvmField val tempFolder = TemporaryFolder()

    @Before
    fun setUp() {
        tempFolder.create()
    }

    @Test
    fun testDetectAbsolutePathToFile() {
        val testFile = tempFolder.newFile("test_sample.kt")
        val text = "Error found in ${testFile.absolutePath} at line 10"

        val matches = FilePathUtils.detectFilePaths(text, tempFolder.root)
        assertEquals(1, matches.size)

        val match = matches[0]
        assertEquals(testFile.absolutePath, match.file.absolutePath)
        assertTrue(match.file.isFile)
        assertFalse(match.file.isDirectory)
    }

    @Test
    fun testDetectAbsolutePathToDirectory() {
        val testDir = tempFolder.newFolder("test_dir")
        val text = "Project created at ${testDir.absolutePath} successfully"

        val matches = FilePathUtils.detectFilePaths(text, tempFolder.root)
        assertEquals(1, matches.size)

        val match = matches[0]
        assertEquals(testDir.absolutePath, match.file.absolutePath)
        assertFalse(match.file.isFile)
        assertTrue(match.file.isDirectory)
    }

    @Test
    fun testDetectRelativePathWithProjectRoot() {
        val projectRoot = tempFolder.newFolder("my_project")
        val subDir = File(projectRoot, "src/main/java").apply { mkdirs() }
        val sourceFile = File(subDir, "Main.kt").apply { createNewFile() }

        val text = "Compiling src/main/java/Main.kt:42 ..."

        val matches = FilePathUtils.detectFilePaths(text, projectRoot)
        assertEquals(1, matches.size)

        val match = matches[0]
        assertEquals(sourceFile.absolutePath, match.file.absolutePath)
        assertTrue(match.file.isFile)
    }

    @Test
    fun testNonExistentFileReturnsEmpty() {
        val text = "Error in /non/existent/path/foo.kt:100"
        val matches = FilePathUtils.detectFilePaths(text, tempFolder.root)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun testResolveFile() {
        val tempFile = tempFolder.newFile("resolve_me.txt")
        val resolvedDirect = FilePathUtils.resolveFile(tempFile.absolutePath, null)
        assertNotNull(resolvedDirect)
        assertEquals(tempFile.absolutePath, resolvedDirect!!.absolutePath)

        val projectRoot = tempFolder.newFolder("proj_root")
        val relFile = File(projectRoot, "rel.txt").apply { createNewFile() }
        val resolvedRel = FilePathUtils.resolveFile("rel.txt", projectRoot)
        assertNotNull(resolvedRel)
        assertEquals(relFile.absolutePath, resolvedRel!!.absolutePath)
    }
}
