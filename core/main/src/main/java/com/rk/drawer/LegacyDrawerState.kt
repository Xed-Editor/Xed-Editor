package com.rk.drawer

import com.rk.file.FileObject
import com.rk.filetree.FileTreeTab
import java.io.Externalizable
import java.io.File
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectStreamClass

/**
 * The drawer state as versions before [SavedDrawerState] saved it: with Java serialization, in three files.
 * [DrawerPersistence] migrates it once, so it can be removed when few installs still have these files.
 */
internal object LegacyDrawerState {
    private const val DRAWER_TABS = "drawerTabs"
    private const val CURRENT_DRAWER_TAB = "currentDrawerTab"
    private const val EXPANDED_FILE_TREE_NODES = "expandedFileTree"

    private val fileNames = listOf(DRAWER_TABS, CURRENT_DRAWER_TAB, EXPANDED_FILE_TREE_NODES)

    fun exists(filesDir: File): Boolean = fileNames.any { File(filesDir, it).exists() }

    /** Reads the legacy files. One that cannot be read is passed to [onError] and left out. */
    fun read(filesDir: File, onError: (Throwable) -> Unit): SavedDrawerState {
        fun readObject(name: String): Any? {
            val file = File(filesDir, name)
            if (!file.exists()) return null
            return runCatching { file.inputStream().use { SerialVersionTolerantObjectInputStream(it).readObject() } }
                .onFailure(onError)
                .getOrNull()
        }

        // A file object that no longer resolves, such as an invalid content URI, is left out.
        fun Any?.toSavedFileOrNull(): SavedFile? = runCatching { (this as? FileObject)?.toSavedFile() }.getOrNull()

        val tabs = (readObject(DRAWER_TABS) as? List<*>).orEmpty().filterIsInstance<FileTreeTab>()
        val selectedTab = readObject(CURRENT_DRAWER_TAB) as? FileTreeTab
        val expandedNodes = (readObject(EXPANDED_FILE_TREE_NODES) as? Map<*, *>).orEmpty()

        return SavedDrawerState(
            projects = tabs.mapNotNull { it.root.toSavedFileOrNull() },
            selectedProject = selectedTab?.root.toSavedFileOrNull(),
            expandedNodes =
                expandedNodes.mapNotNull { (root, nodes) ->
                    val savedRoot = root.toSavedFileOrNull() ?: return@mapNotNull null
                    SavedExpandedNodes(
                        savedRoot,
                        (nodes as? Collection<*>).orEmpty().mapNotNull { it.toSavedFileOrNull() },
                    )
                },
        )
    }

    fun delete(filesDir: File) {
        fileNames.forEach { File(filesDir, it).delete() }
    }
}

/**
 * An [ObjectInputStream] that keeps reading the app's own classes after their `serialVersionUID` changed, as long as
 * their serialized fields did not.
 *
 * The classes in the legacy drawer state (`DrawerTab`, `FileTreeTab` and the file objects) do not declare a
 * `serialVersionUID`, so it is computed from their methods, and almost every release changed it. A plain
 * [ObjectInputStream] rejects such files, although their fields still fit. This stream reads them with the local class
 * descriptor instead, but only where that is safe: the class belongs to the app (`com.rk.`), uses the default
 * serialized form (neither `readObject`/`writeObject` nor [Externalizable]), and the stream lists the same serializable
 * fields, with the same types and in the same order, as the local class. Anything else is rejected as usual.
 */
internal class SerialVersionTolerantObjectInputStream(input: InputStream) : ObjectInputStream(input) {

    override fun readClassDescriptor(): ObjectStreamClass {
        val streamDescriptor = super.readClassDescriptor()
        if (!streamDescriptor.name.startsWith(TOLERATED_PACKAGE_PREFIX)) return streamDescriptor

        val localClass =
            try {
                resolveClass(streamDescriptor)
            } catch (_: ClassNotFoundException) {
                return streamDescriptor
            }
        val localDescriptor = ObjectStreamClass.lookup(localClass) ?: return streamDescriptor

        val tolerated =
            localDescriptor.serialVersionUID != streamDescriptor.serialVersionUID &&
                localClass.usesDefaultSerializedForm() &&
                hasSameFields(localDescriptor, streamDescriptor)

        return if (tolerated) localDescriptor else streamDescriptor
    }

    private fun Class<*>.usesDefaultSerializedForm(): Boolean =
        !Externalizable::class.java.isAssignableFrom(this) &&
            declaredMethods.none { it.name == "readObject" || it.name == "writeObject" }

    private fun hasSameFields(local: ObjectStreamClass, stream: ObjectStreamClass): Boolean {
        val localFields = local.fields
        val streamFields = stream.fields
        return localFields.size == streamFields.size &&
            localFields.zip(streamFields).all { (localField, streamField) ->
                localField.name == streamField.name &&
                    localField.typeCode == streamField.typeCode &&
                    localField.typeString == streamField.typeString
            }
    }

    private companion object {
        const val TOLERATED_PACKAGE_PREFIX = "com.rk."
    }
}
