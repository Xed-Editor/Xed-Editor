package com.rk.drawer

import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.ZipFileObject
import com.rk.filetree.FileTreeTab
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamConstants
import java.io.Serializable
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

// Shaped like the legacy drawer state: an abstract tab without fields, a subclass holding a file object, and the file
// object. None of them declares a serialVersionUID.
private abstract class SampleTab : Serializable

private class SampleFileTab(val root: SampleFile) : SampleTab()

private class SampleFile(val path: String, val isTree: Boolean) : Serializable {
    override fun equals(other: Any?) = other is SampleFile && other.path == path && other.isTree == isTree

    override fun hashCode() = path.hashCode()
}

private class SampleWithCustomForm(val value: String) : Serializable {
    private fun writeObject(output: ObjectOutputStream) = output.defaultWriteObject()

    private fun readObject(input: ObjectInputStream) = input.defaultReadObject()
}

class LegacyDrawerStateTest {
    @get:Rule val folder = TemporaryFolder()

    private val project = FileWrapper(File("/sdcard/project"))
    private val other = FileWrapper(File("/sdcard/other"))
    private val zipEntry = ZipFileObject(FileWrapper(File("/sdcard/project/libs.zip")), "lib/")

    @Test
    fun `migrates the drawer state of earlier versions`() {
        // Earlier versions computed the serialVersionUIDs of these classes, and nearly every release changed them.
        fun ByteArray.asWrittenEarlier() =
            withSerialVersionUid(DrawerTab::class.java, -1L)
                .withSerialVersionUid(FileTreeTab::class.java, -2L)
                .withSerialVersionUid(FileWrapper::class.java, -3L)

        writeLegacyFile(
            "drawerTabs",
            serialize(arrayListOf(FileTreeTab(project), FileTreeTab(other))).asWrittenEarlier(),
        )
        writeLegacyFile("currentDrawerTab", serialize(FileTreeTab(other)).asWrittenEarlier())
        writeLegacyFile(
            "expandedFileTree",
            serialize(linkedMapOf<FileObject, Set<FileObject>>(project to hashSetOf(zipEntry)))
                .withSerialVersionUid(FileWrapper::class.java, -3L)
                .withSerialVersionUid(ZipFileObject::class.java, -4L),
        )

        val errors = mutableListOf<Throwable>()
        val state = LegacyDrawerState.read(folder.root) { errors += it }

        assertEquals(emptyList<Throwable>(), errors)
        assertEquals(
            SavedDrawerState(
                projects = listOf(SavedFile.Local("/sdcard/project"), SavedFile.Local("/sdcard/other")),
                selectedProject = SavedFile.Local("/sdcard/other"),
                expandedNodes =
                    listOf(
                        SavedExpandedNodes(
                            SavedFile.Local("/sdcard/project"),
                            listOf(SavedFile.ZipEntry(SavedFile.Local("/sdcard/project/libs.zip"), "lib/")),
                        )
                    ),
            ),
            state,
        )
    }

    @Test
    fun `leaves out a legacy file that cannot be read`() {
        File(folder.root, "drawerTabs").writeText("not a serialized object")
        writeLegacyFile("currentDrawerTab", serialize(FileTreeTab(other)))

        val errors = mutableListOf<Throwable>()
        val state = LegacyDrawerState.read(folder.root) { errors += it }

        assertEquals(1, errors.size)
        assertEquals(SavedDrawerState(selectedProject = SavedFile.Local("/sdcard/other")), state)
    }

    @Test
    fun `finds and deletes the legacy files`() {
        assertFalse(LegacyDrawerState.exists(folder.root))

        writeLegacyFile("expandedFileTree", serialize(linkedMapOf<FileObject, Set<FileObject>>()))
        assertTrue(LegacyDrawerState.exists(folder.root))

        LegacyDrawerState.delete(folder.root)
        assertFalse(LegacyDrawerState.exists(folder.root))
    }

    @Test
    fun `reads app classes whose serialVersionUID changed but whose fields did not`() {
        val saved =
            serialize(arrayListOf(SampleFileTab(SampleFile("/sdcard/project", isTree = false))))
                .withSerialVersionUid(SampleTab::class.java, 1L)
                .withSerialVersionUid(SampleFileTab::class.java, 2L)
                .withSerialVersionUid(SampleFile::class.java, 3L)

        assertThrows(InvalidClassException::class.java) { ObjectInputStream(saved.inputStream()).readObject() }

        val tabs = readTolerantly(saved) as List<*>
        assertEquals(SampleFile("/sdcard/project", isTree = false), (tabs.single() as SampleFileTab).root)
    }

    @Test
    fun `reads hashed collections of app classes`() {
        val project = SampleFile("/sdcard/project", isTree = true)
        val expandedNodes = linkedMapOf(project to hashSetOf(SampleFile("/sdcard/project/src", isTree = true)))
        val saved = serialize(expandedNodes).withSerialVersionUid(SampleFile::class.java, 3L)

        assertEquals(expandedNodes, readTolerantly(saved))
    }

    @Test
    fun `still rejects app classes whose fields changed`() {
        val saved =
            serialize(SampleFile("/sdcard/project", isTree = false))
                .withSerialVersionUid(SampleFile::class.java, 3L)
                .replaceFirst("path", "name") // as if the field had been renamed since

        assertThrows(InvalidClassException::class.java) { readTolerantly(saved) }
    }

    @Test
    fun `still rejects app classes with a custom serialized form`() {
        val saved = serialize(SampleWithCustomForm("value")).withSerialVersionUid(SampleWithCustomForm::class.java, 3L)

        assertThrows(InvalidClassException::class.java) { readTolerantly(saved) }
    }

    @Test
    fun `still rejects classes outside the app`() {
        val saved = serialize(arrayListOf("value")).withSerialVersionUid(ArrayList::class.java, 3L)

        assertThrows(InvalidClassException::class.java) { readTolerantly(saved) }
    }

    private fun writeLegacyFile(name: String, bytes: ByteArray) = File(folder.root, name).writeBytes(bytes)

    private fun serialize(value: Any): ByteArray {
        val bytes = ByteArrayOutputStream()
        ObjectOutputStream(bytes).use { it.writeObject(value) }
        return bytes.toByteArray()
    }

    private fun readTolerantly(bytes: ByteArray): Any? =
        SerialVersionTolerantObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() }

    /** Replaces the serialVersionUID in the class descriptor of [type], as another version of it would have written. */
    private fun ByteArray.withSerialVersionUid(type: Class<*>, serialVersionUid: Long): ByteArray {
        val name = type.name.toByteArray(Charsets.UTF_8)
        val descriptor =
            byteArrayOf(ObjectStreamConstants.TC_CLASSDESC, (name.size shr 8).toByte(), name.size.toByte()) + name
        val index = indexOf(descriptor)
        check(index >= 0) { "No class descriptor for ${type.name}" }

        return copyOf().also { ByteBuffer.wrap(it, index + descriptor.size, Long.SIZE_BYTES).putLong(serialVersionUid) }
    }

    private fun ByteArray.replaceFirst(old: String, new: String): ByteArray {
        require(old.length == new.length)
        val index = indexOf(old.toByteArray(Charsets.UTF_8))
        check(index >= 0) { "\"$old\" not found" }

        return copyOf().also { new.toByteArray(Charsets.UTF_8).copyInto(it, index) }
    }

    private fun ByteArray.indexOf(bytes: ByteArray): Int =
        (0..size - bytes.size).firstOrNull { start -> bytes.indices.all { this[start + it] == bytes[it] } } ?: -1
}
