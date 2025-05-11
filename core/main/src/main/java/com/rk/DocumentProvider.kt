package com.rk

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.rk.libcommons.alpineHomeDir
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.xededitor.R
import java.io.File
import java.io.FileNotFoundException

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager


class DocumentProvider : DocumentsProvider() {

    override fun onCreate(): Boolean {
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_ROOT_PROJECTION)
        val row = result.newRow()
        row.add(Root.COLUMN_ROOT_ID, "my_root")
        row.add(Root.COLUMN_DOCUMENT_ID, "root")
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE)
        row.add(Root.COLUMN_MIME_TYPES, "*/*")
        row.add(Root.COLUMN_FLAGS,
            Root.FLAG_SUPPORTS_CREATE or
                    Root.FLAG_SUPPORTS_IS_CHILD or
                    Root.FLAG_LOCAL_ONLY)
        val file = alpineHomeDir()
        val stat = android.os.StatFs(file.path)
        val availableBytes = stat.availableBytes
        row.add(Root.COLUMN_AVAILABLE_BYTES, availableBytes)
        row.add(Root.COLUMN_ICON, R.drawable.ic_launcher)
        row.add(Root.COLUMN_TITLE, strings.app_name.getString())
        row.add(Root.COLUMN_SUMMARY, "Access your Alpine files")
        return result
    }


    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)
        val file = getFileForDocId(documentId)
        result.newRow().apply {
            add(Document.COLUMN_DOCUMENT_ID, documentId)
            add(Document.COLUMN_DISPLAY_NAME, file.name)
            add(Document.COLUMN_SIZE, file.length())
            add(Document.COLUMN_MIME_TYPE, getMimeType(file))
            add(Root.COLUMN_ICON, R.drawable.ic_launcher)
            add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME)
        }
        return result
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val parent = getFileForDocId(parentDocumentId)
        val result = MatrixCursor(projection ?: DEFAULT_DOCUMENT_PROJECTION)

        parent.listFiles()?.forEach { file ->
            result.newRow().apply {
                add(Document.COLUMN_DOCUMENT_ID, getDocIdForFile(file))
                add(Document.COLUMN_DISPLAY_NAME, file.name)
                add(Document.COLUMN_SIZE, file.length())
                add(Document.COLUMN_MIME_TYPE, getMimeType(file))
                add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_WRITE or Document.FLAG_SUPPORTS_COPY or Document.FLAG_SUPPORTS_MOVE or Document.FLAG_SUPPORTS_DELETE or Document.FLAG_SUPPORTS_RENAME)
            }
        }

        return result
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor? {
        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)

        if (!file.exists()) {
            throw FileNotFoundException("File not found: $file")
        }

        if (file.isDirectory) {
            throw FileNotFoundException("Cannot open directory: $file")
        }

        return ParcelFileDescriptor.open(file, accessMode)
    }


    private fun getFileForDocId(docId: String): File {
        val base = alpineHomeDir().canonicalFile
        return when (docId) {
            "root" -> base
            else -> {
                val target = File(base, docId).canonicalFile
                if (!target.path.startsWith(base.path)) base else target
            }
        }
    }


    private fun getDocIdForFile(file: File): String {
        val base = alpineHomeDir().canonicalFile
        return file.relativeTo(base).path
    }

    private fun getMimeType(file: File): String {
        return if (file.isDirectory) {
            Document.MIME_TYPE_DIR
        } else {
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
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
            Document.COLUMN_FLAGS
        )
    }
}
