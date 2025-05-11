package com.rk

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
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
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

class DocumentProvider : DocumentsProvider() {
    private val TAG = "AlpineDocumentProvider"

    // Define a constant for the root document ID
    private val ROOT_DOC_ID = "root"

    override fun onCreate(): Boolean {
        Log.d(TAG, "onCreate")
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryRoots")
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()

        // Use a consistent root ID
        val rootId = "alpine_root"
        row.add(Root.COLUMN_ROOT_ID, rootId)
        row.add(Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)

        // Combine all flags in one assignment
        val flags = Root.FLAG_SUPPORTS_CREATE or
                Root.FLAG_SUPPORTS_IS_CHILD or
                Root.FLAG_LOCAL_ONLY
        row.add(Root.COLUMN_FLAGS, flags)

        row.add(Root.COLUMN_MIME_TYPES, "*/*")

        val file = alpineHomeDir()
        Log.d(TAG, "Root directory: ${file.absolutePath}, exists: ${file.exists()}, readable: ${file.canRead()}")

        if (file.exists()) {
            val stat = android.os.StatFs(file.path)
            val availableBytes = stat.availableBytes
            row.add(Root.COLUMN_AVAILABLE_BYTES, availableBytes)
        } else {
            Log.e(TAG, "Root directory doesn't exist!")
        }

        row.add(Root.COLUMN_ICON, R.drawable.ic_launcher)
        row.add(Root.COLUMN_TITLE, strings.app_name.getString())
        row.add(Root.COLUMN_SUMMARY, "Access your Alpine files")

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        Log.d(TAG, "queryDocument: $documentId")
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = getFileForDocId(documentId)

        Log.d(TAG, "File for docId $documentId: ${file.absolutePath}, exists: ${file.exists()}, readable: ${file.canRead()}")

        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, getMimeType(file))

            // Set appropriate flags based on whether this is a file or directory
            val flags = if (file.isDirectory) {
                Document.FLAG_DIR_SUPPORTS_CREATE or
                        Document.FLAG_SUPPORTS_DELETE or
                        Document.FLAG_SUPPORTS_RENAME
            } else {
                Document.FLAG_SUPPORTS_WRITE or
                        Document.FLAG_SUPPORTS_COPY or
                        Document.FLAG_SUPPORTS_MOVE or
                        Document.FLAG_SUPPORTS_DELETE or
                        Document.FLAG_SUPPORTS_RENAME
            }

            add(Document.COLUMN_FLAGS, flags)
            add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
        }

        return result
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        Log.d(TAG, "queryChildDocuments: $parentDocumentId")
        val parent = getFileForDocId(parentDocumentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        Log.d(TAG, "Parent directory: ${parent.absolutePath}, exists: ${parent.exists()}, readable: ${parent.canRead()}")

        if (parent.exists() && parent.isDirectory) {
            val files = parent.listFiles()
            Log.d(TAG, "Found ${files?.size ?: 0} children")

            files?.forEach { file ->
                val docId = getDocIdForFile(file)
                Log.d(TAG, "Child: ${file.name}, docId: $docId")

                result.newRow().apply {
                    add(Document.COLUMN_DOCUMENT_ID, docId)
                    add(Document.COLUMN_DISPLAY_NAME, file.name)
                    add(Document.COLUMN_SIZE, file.length())
                    add(Document.COLUMN_MIME_TYPE, getMimeType(file))

                    // Set appropriate flags based on whether this is a file or directory
                    val flags = if (file.isDirectory) {
                        Document.FLAG_DIR_SUPPORTS_CREATE or
                                Document.FLAG_SUPPORTS_DELETE or
                                Document.FLAG_SUPPORTS_RENAME
                    } else {
                        Document.FLAG_SUPPORTS_WRITE or
                                Document.FLAG_SUPPORTS_COPY or
                                Document.FLAG_SUPPORTS_MOVE or
                                Document.FLAG_SUPPORTS_DELETE or
                                Document.FLAG_SUPPORTS_RENAME
                    }

                    add(Document.COLUMN_FLAGS, flags)
                    add(Document.COLUMN_LAST_MODIFIED, file.lastModified())
                }
            }
        } else {
            Log.e(TAG, "Parent directory doesn't exist or isn't a directory!")
        }

        return result
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        Log.d(TAG, "openDocument: $documentId, mode: $mode")
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)

        if (!file.exists()) {
            Log.e(TAG, "File not found: ${file.absolutePath}")
            throw FileNotFoundException("File not found: $file")
        }

        if (file.isDirectory) {
            Log.e(TAG, "Cannot open directory: ${file.absolutePath}")
            throw FileNotFoundException("Cannot open directory: $file")
        }

        return ParcelFileDescriptor.open(file, accessMode)
    }

    // Added implementation for document creation
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        Log.d(TAG, "createDocument: parent=$parentDocumentId, mimeType=$mimeType, name=$displayName")
        val parent = getFileForDocId(parentDocumentId)

        if (!parent.exists()) {
            Log.e(TAG, "Parent directory doesn't exist: ${parent.absolutePath}")
            throw FileNotFoundException("Parent not found: $parent")
        }

        if (!parent.isDirectory) {
            Log.e(TAG, "Parent is not a directory: ${parent.absolutePath}")
            throw FileNotFoundException("Parent is not a directory: $parent")
        }

        val file = File(parent, displayName)
        if (Document.MIME_TYPE_DIR == mimeType) {
            if (!file.mkdir()) {
                Log.e(TAG, "Failed to create directory: ${file.absolutePath}")
                throw IOException("Failed to create directory: $file")
            }
        } else {
            if (!file.createNewFile()) {
                Log.e(TAG, "Failed to create file: ${file.absolutePath}")
                throw IOException("Failed to create file: $file")
            }
        }

        return getDocIdForFile(file)
    }

    // CRITICAL FIX: The issue is with handling hidden files like ".ash_history"
    private fun getFileForDocId(docId: String): File {
        Log.d(TAG, "getFileForDocId: $docId")
        val base = alpineHomeDir()

        return when (docId) {
            ROOT_DOC_ID -> base
            else -> {
                // Make sure we handle absolute paths properly
                val targetFile = File(base, docId)

                // IMPORTANT: Validate that the file is actually inside the base directory
                // This prevents the "not a descendant of root" error
                if (!targetFile.canonicalPath.startsWith(base.canonicalPath)) {
                    Log.e(TAG, "Security violation: $docId is not within ${base.canonicalPath}")
                    throw SecurityException("Document $docId is not a descendant of root")
                }

                targetFile
            }
        }
    }

    // CRITICAL FIX: This method is completely rewritten to correctly handle document IDs
    private fun getDocIdForFile(file: File): String {
        val base = alpineHomeDir()

        // If this is the root directory itself
        if (file.canonicalPath == base.canonicalPath) {
            return ROOT_DOC_ID
        }

        // Make sure the file is inside the base directory
        if (!file.canonicalPath.startsWith(base.canonicalPath)) {
            Log.e(TAG, "File is outside base directory: ${file.canonicalPath}")
            throw SecurityException("File ${file.name} is not a descendant of root")
        }

        // Get the path relative to the base directory
        // We need to ensure we're handling hidden files (starting with .) correctly
        val relativePath = file.canonicalPath.substring(base.canonicalPath.length)
            .trimStart(File.separatorChar)

        Log.d(TAG, "getDocIdForFile: ${file.absolutePath} -> $relativePath")
        return relativePath
    }

    // Override isChildDocument to properly validate parent-child relationships
    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        // Root is parent of everything
        if (parentDocumentId == ROOT_DOC_ID) {
            if (documentId == ROOT_DOC_ID) {
                return false
            }

            // Make sure the document doesn't contain path separators (direct child)
            if (!documentId.contains(File.separator)) {
                return true
            }

            // Otherwise check if it's a proper subdirectory
            val parentPath = documentId.substringBeforeLast(File.separator, "")
            return parentPath.isEmpty()
        }

        // For non-root documents, check if one is a direct parent of the other
        return documentId.startsWith(parentDocumentId + File.separator)
    }

    private fun getMimeType(file: File): String {
        return if (file.isDirectory) {
            Document.MIME_TYPE_DIR
        } else {
            val extension = file.extension.lowercase()
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
        }
    }

    companion object {
        fun isDocumentProviderEnabled(context: Context): Boolean {
            val componentName = ComponentName(context, DocumentProvider::class.java)
            val state = context.packageManager.getComponentEnabledSetting(componentName)
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                    state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        }

        fun setDocumentProviderEnabled(context: Context, enabled: Boolean) {
            val componentName = ComponentName(context, DocumentProvider::class.java)
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

        val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_ICON,
            Root.COLUMN_FLAGS,
            Root.COLUMN_AVAILABLE_BYTES,
            Root.COLUMN_MIME_TYPES
        )

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_LAST_MODIFIED
        )
    }
}