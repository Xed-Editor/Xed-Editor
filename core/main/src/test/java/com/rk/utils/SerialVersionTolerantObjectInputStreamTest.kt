package com.rk.utils

import com.rk.drawer.DrawerTab
import com.rk.file.FileWrapper
import com.rk.file.NetWrapper
import com.rk.file.UriWrapper
import com.rk.file.ZipFileObject
import com.rk.filetree.FileTreeTab
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InvalidClassException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.ObjectStreamClass
import java.io.ObjectStreamConstants
import java.io.Serializable
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

// Shaped like the drawer state: an abstract tab without fields, a subclass holding a file object, and the file object.
// None of them declares a serialVersionUID.
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

class SerialVersionTolerantObjectInputStreamTest {

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

    @Test
    fun `classes in the drawer state declare their serialVersionUID`() {
        val persistedClasses =
            listOf(
                DrawerTab::class.java,
                FileTreeTab::class.java,
                FileWrapper::class.java,
                UriWrapper::class.java,
                ZipFileObject::class.java,
                NetWrapper::class.java,
            )

        for (persistedClass in persistedClasses) {
            assertEquals(persistedClass.name, 1L, ObjectStreamClass.lookup(persistedClass).serialVersionUID)
        }
    }

    @Test
    fun `reads drawer state saved before the serialVersionUIDs were declared`() {
        val project = FileWrapper(File("/sdcard/project"))
        val zipEntry = ZipFileObject(FileWrapper(File("/sdcard/project/libs.zip")), "lib/")

        // Until now these classes had computed serialVersionUIDs, which changed with almost every release.
        val savedTabs =
            serialize(arrayListOf(FileTreeTab(project)))
                .withSerialVersionUid(DrawerTab::class.java, -4000L)
                .withSerialVersionUid(FileTreeTab::class.java, -4001L)
                .withSerialVersionUid(FileWrapper::class.java, -4002L)
        val savedExpandedNodes =
            serialize(linkedMapOf(project to hashSetOf(zipEntry)))
                .withSerialVersionUid(FileWrapper::class.java, -4002L)
                .withSerialVersionUid(ZipFileObject::class.java, -4003L)

        assertEquals(listOf(FileTreeTab(project)), readTolerantly(savedTabs))
        assertEquals(mapOf(project to setOf(zipEntry)), readTolerantly(savedExpandedNodes))
    }

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
