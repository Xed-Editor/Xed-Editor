package com.rk.drawer

import android.net.Uri
import android.provider.DocumentsContract
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.file.UriWrapper
import com.rk.file.ZipFileObject
import java.io.File
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The drawer state as [DrawerPersistence] saves it, in JSON. */
@Serializable
internal data class SavedDrawerState(
    /** The root of each file tree tab, in the order of the tabs. */
    val projects: List<SavedFile> = emptyList(),
    /** The root of the selected tab, if one is selected. */
    val selectedProject: SavedFile? = null,
    /** The expanded directories, per project root. */
    val expandedNodes: List<SavedExpandedNodes> = emptyList(),
) {
    fun toJson(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): SavedDrawerState = json.decodeFromString(text)
    }
}

@Serializable internal data class SavedExpandedNodes(val root: SavedFile, val nodes: List<SavedFile>)

/** A [FileObject] in the saved drawer state. */
@Serializable
internal sealed interface SavedFile {
    /** A file on disk, see [FileWrapper]. */
    @Serializable @SerialName("file") data class Local(val path: String) : SavedFile

    /** A document behind a content URI, see [UriWrapper]. */
    @Serializable @SerialName("uri") data class Document(val uri: String) : SavedFile

    /** An entry in a ZIP archive, see [ZipFileObject]. */
    @Serializable @SerialName("zip") data class ZipEntry(val archive: SavedFile, val path: String) : SavedFile
}

/** How this file object is saved, or null for a kind of file object that the drawer state does not keep. */
internal fun FileObject.toSavedFile(): SavedFile? =
    when (this) {
        is FileWrapper -> SavedFile.Local(file.absolutePath)
        is UriWrapper -> SavedFile.Document(getAbsolutePath())
        is ZipFileObject -> zipFileObject.toSavedFile()?.let { SavedFile.ZipEntry(it, entryPath) }
        else -> null
    }

internal fun SavedFile.toFileObject(): FileObject =
    when (this) {
        is SavedFile.Local -> FileWrapper(File(path))
        is SavedFile.Document -> Uri.parse(uri).let { UriWrapper(it, isTree = DocumentsContract.isTreeUri(it)) }
        is SavedFile.ZipEntry -> ZipFileObject(archive.toFileObject(), path)
    }
