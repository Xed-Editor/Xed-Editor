package com.rk.drawer

import com.rk.file.FileWrapper
import com.rk.file.NetWrapper
import com.rk.file.ZipFileObject
import java.io.File
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedDrawerStateTest {

    @Test
    fun `round trips through JSON`() {
        val state =
            SavedDrawerState(
                projects =
                    listOf(
                        SavedFile.Local("/sdcard/project"),
                        SavedFile.Document(
                            "content://com.termux.documents/tree/%2Fdata%2Fdata%2Fcom.termux%2Ffiles%2Fhome"
                        ),
                    ),
                selectedProject = SavedFile.Local("/sdcard/project"),
                expandedNodes =
                    listOf(
                        SavedExpandedNodes(
                            SavedFile.Local("/sdcard/project"),
                            listOf(
                                SavedFile.Local("/sdcard/project/src"),
                                SavedFile.ZipEntry(SavedFile.Local("/sdcard/project/libs.zip"), "lib/"),
                            ),
                        )
                    ),
            )

        assertEquals(state, SavedDrawerState.fromJson(state.toJson()))
    }

    @Test
    fun `writes the expected JSON`() {
        val state =
            SavedDrawerState(
                projects = listOf(SavedFile.Local("/sdcard/project")),
                selectedProject = SavedFile.Local("/sdcard/project"),
            )

        assertEquals(
            """{"projects":[{"type":"file","path":"/sdcard/project"}],""" +
                """"selectedProject":{"type":"file","path":"/sdcard/project"}}""",
            state.toJson(),
        )
    }

    @Test
    fun `ignores what a newer version adds`() {
        val json = """{"projects":[{"type":"file","path":"/sdcard/project"}],"pinnedProjects":[]}"""

        assertEquals(
            SavedDrawerState(projects = listOf(SavedFile.Local("/sdcard/project"))),
            SavedDrawerState.fromJson(json),
        )
    }

    @Test
    fun `converts file objects`() {
        val project = FileWrapper(File("/sdcard/project"))
        val zipEntry = ZipFileObject(FileWrapper(File("/sdcard/project/libs.zip")), "lib/")

        assertEquals(SavedFile.Local("/sdcard/project"), project.toSavedFile())
        assertEquals(SavedFile.ZipEntry(SavedFile.Local("/sdcard/project/libs.zip"), "lib/"), zipEntry.toSavedFile())
        assertEquals(project, project.toSavedFile()?.toFileObject())
        assertEquals(zipEntry, zipEntry.toSavedFile()?.toFileObject())
    }

    @Test
    fun `leaves out file objects the drawer does not keep`() {
        assertNull(NetWrapper(URL("https://example.com/notes.txt")).toSavedFile())
    }
}
