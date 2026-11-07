package com.rk.utils

import androidx.core.util.AtomicFile
import java.io.File
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileObjectTest {
    @get:Rule val folder = TemporaryFolder()

    private val baseFile
        get() = File(folder.root, "state")

    private val file
        get() = AtomicFile(baseFile)

    @Test
    fun `reads back what was written and leaves no temporary file behind`() {
        runBlocking {
            file.writeObject(arrayListOf("a", "b"))

            assertEquals(listOf("a", "b"), file.readObject())
            assertEquals(listOf("state"), folder.root.list()?.toList())
        }
    }

    @Test
    fun `keeps the previous content if writing fails`() {
        runBlocking {
            file.writeObject(arrayListOf("previous"))

            assertThrows(NotSerializableException::class.java) { runBlocking { file.writeObject(arrayListOf(Any())) } }

            assertEquals(listOf("previous"), file.readObject())
            assertEquals(listOf("state"), folder.root.list()?.toList())
        }
    }

    @Test
    fun `reads nothing if nothing was written`() {
        runBlocking { assertNull(file.readObject()) }
    }

    @Test
    fun `reads files that were written before writes were atomic`() {
        runBlocking {
            ObjectOutputStream(baseFile.outputStream()).use { it.writeObject(arrayListOf("old")) }

            assertEquals(listOf("old"), file.readObject())
        }
    }
}
