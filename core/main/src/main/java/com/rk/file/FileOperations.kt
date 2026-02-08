package com.rk.file

import android.content.Context
import android.content.Intent
import com.rk.activities.main.MainActivity
import com.rk.components.ContentProgress
import com.rk.utils.errorDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.io.Util

object FileOperations {
    var clipboard: FileObject? = null
    var isCut: Boolean = false

    fun copyToClipboard(file: FileObject, isCut: Boolean = false) {
        clipboard = file
        this.isCut = isCut
    }

    suspend fun deleteFile(file: FileObject): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            errorDialog(e)
            false
        }
    }

    suspend fun openWithExternalApp(context: Context, file: FileObject) {
        openWith(context, file)
    }

    fun saveAs(file: FileObject) {
        to_save_file = file
        MainActivity.instance?.fileManager?.requestOpenDirectoryToSaveFile(file)
    }

    fun addFile(parentFile: FileObject) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        intent.addCategory(Intent.CATEGORY_OPENABLE)
        intent.type = "*/*"
        MainActivity.instance?.fileManager?.requestAddFile(parentFile)
    }

    suspend fun calculateContent(folder: FileObject, onProgress: (ContentProgress) -> Unit = {}) {
        var totalSize = 0L
        var totalItems = 0L

        val stack = ArrayDeque<FileObject>()
        stack.add(folder)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.isDirectory()) {
                stack.addAll(current.listFiles())
                totalItems++
                onProgress(ContentProgress(totalSize, totalItems))
            } else {
                totalSize += current.length()
                totalItems++
                onProgress(ContentProgress(totalSize, totalItems))
            }
        }
    }

    /**
     * Pastes a file or folder to the destination directory
     *
     * @param context Android context for content resolver operations
     * @param sourceFile The file or folder to copy/move
     * @param destinationFolder The target directory
     * @param isCut Whether to move (cut) or copy the file
     * @param onProgress Optional callback for progress updates (current file being processed)
     * @return Result indicating success or failure with error message
     */
    suspend fun pasteFile(
        context: Context,
        sourceFile: FileObject,
        destinationFolder: FileObject,
        isCut: Boolean = false,
        onProgress: ((String) -> Unit)? = null,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                // Validation checks
                if (!destinationFolder.isDirectory()) {
                    throw IllegalArgumentException("Destination must be a directory")
                }

                if (!destinationFolder.canWrite()) {
                    throw IllegalStateException("Destination directory is not writable")
                }

                if (!sourceFile.exists()) {
                    throw IllegalArgumentException("Source file does not exist")
                }

                // Prevent copying a directory into itself
                if (sourceFile.isDirectory() && isParentOf(sourceFile, destinationFolder)) {
                    throw IllegalArgumentException("Cannot copy a directory into itself")
                }

                // Check if target already exists
                val targetName = sourceFile.getName()
                if (destinationFolder.hasChild(targetName)) {
                    throw IllegalStateException(
                        "A file or folder with name '$targetName' already exists in destination"
                    )
                }

                // Perform the copy operation
                copyRecursive(context, sourceFile, destinationFolder, onProgress)

                // If it's a cut operation, delete the source
                if (isCut) {
                    val deleteSuccess = sourceFile.delete()
                    if (!deleteSuccess) {
                        throw IllegalStateException("Failed to delete source file after moving")
                    }
                }
            }
        }

    /** Recursively copies a file or directory */
    suspend fun copyRecursive(
        context: Context,
        sourceFile: FileObject,
        targetParent: FileObject,
        onProgress: ((String) -> Unit)?,
    ) {
        onProgress?.invoke("Processing: ${sourceFile.getName()}")

        if (sourceFile.isDirectory()) {
            // Create target directory
            val targetDir =
                targetParent.createChild(false, sourceFile.getName())
                    ?: throw IllegalStateException("Failed to create directory: ${sourceFile.getName()}")

            // Copy all children
            sourceFile.listFiles().forEach { child -> copyRecursive(context, child, targetDir, onProgress) }
        } else {
            // Copy file content
            val targetFile =
                targetParent.createChild(true, sourceFile.getName())
                    ?: throw IllegalStateException("Failed to create file: ${sourceFile.getName()}")

            context.contentResolver.openInputStream(sourceFile.toUri())?.use { inputStream ->
                context.contentResolver.openOutputStream(targetFile.toUri())?.use { outputStream ->
                    Util.copyStream(inputStream, outputStream)
                } ?: throw IllegalStateException("Failed to open output stream for: ${sourceFile.getName()}")
            } ?: throw IllegalStateException("Failed to open input stream for: ${sourceFile.getName()}")
        }
    }

    /** Checks if parentDir is a parent of childDir (prevents copying directory into itself) */
    suspend fun isParentOf(parentDir: FileObject, childDir: FileObject): Boolean {
        var current: FileObject? = childDir
        while (current != null) {
            if (current == parentDir) {
                return true
            }
            current = current.getParentFile()
        }
        return false
    }

    /** Convenience function for copying files */
    suspend fun copyFile(
        context: Context,
        sourceFile: FileObject,
        destinationFolder: FileObject,
        onProgress: ((String) -> Unit)? = null,
    ): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = false, onProgress)

    /** Convenience function for moving files */
    suspend fun moveFile(
        context: Context,
        sourceFile: FileObject,
        destinationFolder: FileObject,
        onProgress: ((String) -> Unit)? = null,
    ): Result<Unit> = pasteFile(context, sourceFile, destinationFolder, isCut = true, onProgress)
}
