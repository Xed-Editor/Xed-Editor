package com.rk

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import com.rk.libcommons.alpineHomeDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.R
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.Collections
import java.util.LinkedList
import java.util.Locale

class AlpineDocumentProvider : DocumentsProvider() {
    override fun queryRoots(projection: Array<String>?): Cursor {
        val result = MatrixCursor(
            projection
                ?: DEFAULT_ROOT_PROJECTION
        )
        val applicationName = strings.app_name.getString()

        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, getDocIdForFile(BASE_DIR))
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(BASE_DIR))
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, null)
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH or DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
        )
        row.add(DocumentsContract.Root.COLUMN_TITLE, applicationName)
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, ALL_MIME_TYPES)
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, BASE_DIR.freeSpace)
        row.add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String, projection: Array<String>?): Cursor {
        val result = MatrixCursor(
            projection
                ?: DEFAULT_DOCUMENT_PROJECTION
        )
        includeFile(result, documentId, null)
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String?  // Changed from non-nullable to nullable
    ): Cursor {
        val result = MatrixCursor(
            projection
                ?: DEFAULT_DOCUMENT_PROJECTION
        )
        val parent = getFileForDocId(parentDocumentId)
        val files = parent.listFiles()
        if (files != null) {
            for (file in files) {
                includeFile(result, null, file)
            }
        } else {
            Log.e("DocumentsProvider", "Unable to list files in $parentDocumentId")
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        return ParcelFileDescriptor.open(file, accessMode)
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal
    ): AssetFileDescriptor {
        val file = getFileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, file.length())
    }

    override fun onCreate(): Boolean {
        return true
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        parentDocumentId: String,
        mimeType: String,
        displayName: String
    ): String {
        val parent = getFileForDocId(parentDocumentId)
        var newFile = File(parent, displayName)
        var noConflictId = 2
        while (newFile.exists()) {
            newFile = File(parent, displayName + " (" + noConflictId++ + ")")
        }
        try {
            val succeeded = if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                newFile.mkdir()
            } else {
                newFile.createNewFile()
            }
            if (!succeeded) {
                throw FileNotFoundException("Failed to create document with id " + newFile.absolutePath)
            }
        } catch (e: IOException) {
            throw FileNotFoundException("Failed to create document with id " + newFile.absolutePath)
        }
        return getDocIdForFile(newFile)
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        val file = getFileForDocId(documentId)
        if (!file.delete()) {
            throw FileNotFoundException("Failed to delete document with id $documentId")
        }
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        val file = getFileForDocId(documentId)
        return getMimeType(file)
    }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<String>?
    ): Cursor {
        val result = MatrixCursor(
            projection
                ?: DEFAULT_DOCUMENT_PROJECTION
        )
        val parent = getFileForDocId(rootId)

        // This example implementation searches file names for the query and doesn't rank search
        // results, so we can stop as soon as we find a sufficient number of matches.  Other
        // implementations might rank results and use other data about files, rather than the file
        // name, to produce a match.
        val pending = LinkedList<File>()
        pending.add(parent)

        val MAX_SEARCH_RESULTS = 50
        while (!pending.isEmpty() && result.count < MAX_SEARCH_RESULTS) {
            val file = pending.removeFirst()
            // Avoid directories outside the $HOME directory linked with symlinks (to avoid e.g. search
            // through the whole SD card).
            var isInsideHome: Boolean
            try {
                isInsideHome = file.canonicalPath.startsWith(alpineHomeDir().canonicalPath)
            } catch (e: IOException) {
                isInsideHome = true
            }
            if (isInsideHome) {
                if (file.isDirectory) {
                    file.listFiles()?.let { Collections.addAll(pending, *it) }
                } else {
                    if (file.name.lowercase(Locale.getDefault()).contains(query)) {
                        includeFile(result, null, file)
                    }
                }
            }
        }

        return result
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        return documentId.startsWith(parentDocumentId)
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId  the document ID representing the desired file (may be null if given file)
     * @param file   the File object representing the desired file (may be null if given docID)
     */
    @Throws(FileNotFoundException::class)
    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        var docId = docId
        var file = file
        if (docId == null) {
            docId = getDocIdForFile(file!!)
        } else {
            file = getFileForDocId(docId)
        }

        var flags = 0
        if (file.isDirectory) {
            if (file.canWrite()) flags =
                flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
        } else if (file.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
        }
        if (file.parentFile?.canWrite() == true) flags =
            flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE

        val displayName = file.name
        val mimeType = getMimeType(file)
        if (mimeType.startsWith("image/")) flags =
            flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL

        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, docId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, file.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        row.add(DocumentsContract.Document.COLUMN_ICON, R.mipmap.ic_launcher)
    }

    companion object {
        fun isDocumentProviderEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, AlpineDocumentProvider::class.java)
            val state = context.packageManager.getComponentEnabledSetting(componentName)
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }

        fun setDocumentProviderEnabled(context: Context, enabled: Boolean) {
            if (isDocumentProviderEnabled(context) == enabled) {
                return
            }
            val componentName = ComponentName(context, AlpineDocumentProvider::class.java)
            val newState = if (enabled)
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED

            context.packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                PackageManager.DONT_KILL_APP
            )
        }


        private const val ALL_MIME_TYPES = "*/*"

        private val BASE_DIR = alpineHomeDir()

        // The default columns to return information about a root if no specific
        // columns are requested in a query.
        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
        )

        // The default columns to return information about a document if no specific
        // columns are requested in a query.
        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )

        /**
         * Get the document id given a file. This document id must be consistent across time as other
         * applications may save the ID and use it to reference documents later.
         *
         * The reverse of @{link #getFileForDocId}.
         */
        private fun getDocIdForFile(file: File): String {
            return file.absolutePath
        }

        /**
         * Get the file given a document id (the reverse of [.getDocIdForFile]).
         */
        @Throws(FileNotFoundException::class)
        private fun getFileForDocId(docId: String): File {
            val f = File(docId)
            if (!f.exists()) throw FileNotFoundException(f.absolutePath + " not found")
            return f
        }

        private fun getMimeType(file: File): String {
            if (file.isDirectory) {
                return DocumentsContract.Document.MIME_TYPE_DIR
            } else {
                val name = file.name
                val lastDot = name.lastIndexOf('.')
                if (lastDot >= 0) {
                    val extension = name.substring(lastDot + 1).lowercase(Locale.getDefault())
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    if (mime != null) return mime
                }
                return "application/octet-stream"
            }
        }
    }
}