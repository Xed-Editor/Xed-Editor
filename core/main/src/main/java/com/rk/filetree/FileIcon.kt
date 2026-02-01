package com.rk.filetree

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caverock.androidsvg.SVG
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.icons.pack.currentIconPack
import com.rk.icons.rememberSvgImageLoader
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.theme.folderSurface
import java.io.InputStream

private val plain_file = drawables.file
private val folder = drawables.folder
private val unknown = drawables.unknown_document
private val fileSymlink = drawables.file_symlink
private val archive = drawables.archive
private val text = drawables.text
private val gradle = drawables.gradle
private val info = drawables.info

/**
 * Displays a file icon for a given [FileObject].
 *
 * The icon is tinted with the secondary color if it is a file. If it is a folder or archive, it is tinted with the
 * folder surface color.
 *
 * Supports:
 * - ✔ Icon pack (uses the icon from the icon pack if available, otherwise uses the builtin icon)
 * - ✔ Tint (applyTint property in icon pack or builtin icon tint)
 *
 * @param file The [FileObject] to display the icon for.
 * @param iconTint Optional override for the icon tint.
 * @param isExpanded Whether the file is expanded.
 */
@Composable
fun FileIcon(file: FileObject, iconTint: Color? = null, isExpanded: Boolean = false) {
    val iconPackFile = currentIconPack.value?.getIconFileForFile(file, isExpanded)
    val imageLoader = rememberSvgImageLoader()

    val icon =
        when {
            file.isFile() -> getBuiltInFileIcon(file)
            file.isDirectory() -> folder
            file.isSymlink() -> fileSymlink
            else -> unknown
        }

    val tint =
        iconTint
            ?: if (icon == folder || icon == archive) {
                MaterialTheme.colorScheme.folderSurface
            } else MaterialTheme.colorScheme.secondary

    val useTint = currentIconPack.value?.info?.applyTint == true

    if (iconPackFile != null) {
        AsyncImage(
            model = iconPackFile,
            imageLoader = imageLoader,
            contentDescription = null,
            colorFilter = if (useTint) ColorFilter.tint(tint) else null,
            modifier = Modifier.size(20.dp),
        )
    } else {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Displays a file icon for a given file name.
 *
 * The icon is tinted with the secondary color if it is a file. If it is a folder or archive, it is tinted with the
 * folder surface color.
 *
 * Supports:
 * - ✔ Icon pack (uses the icon from the icon pack if available, otherwise uses the builtin icon)
 * - ✔ Tint (applyTint property in icon pack or builtin icon tint)
 *
 * @param fileName The name of the file.
 * @param isDirectory Whether the file is a directory.
 * @param iconTint Optional override for the icon tint.
 * @param isExpanded Whether the file is expanded.
 */
@Composable
fun FileNameIcon(fileName: String, isDirectory: Boolean, iconTint: Color? = null, isExpanded: Boolean = false) {
    val iconPackFile = currentIconPack.value?.getIconFileForName(fileName, isDirectory, isExpanded)
    val imageLoader = rememberSvgImageLoader()

    val icon = if (isDirectory) folder else getBuiltInFileIcon(fileName)

    val tint =
        iconTint
            ?: if (icon == folder || icon == archive) {
                MaterialTheme.colorScheme.folderSurface
            } else MaterialTheme.colorScheme.secondary

    val useTint = currentIconPack.value?.info?.applyTint == true

    if (iconPackFile != null) {
        AsyncImage(
            model = iconPackFile,
            imageLoader = imageLoader,
            contentDescription = null,
            colorFilter = if (useTint) ColorFilter.tint(tint) else null,
            modifier = Modifier.size(20.dp),
        )
    } else {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * Returns a file icon for a given file name. The icon is not tinted.
 *
 * Supports:
 * - ✔ Icon pack (uses the icon from the icon pack if available, otherwise uses the builtin icon)
 * - ✘ Tint (applyTint property in icon pack or builtin icon tint)
 *
 * @param context The [Context] for operations.
 * @param fileName The name of the file.
 * @param isDirectory Whether the file is a directory.
 * @return A [Drawable] representing the file icon.
 */
fun getDrawableFileIcon(
    context: Context,
    fileName: String,
    isDirectory: Boolean,
    isExpanded: Boolean = false,
): Drawable? {
    fun loadSvg(inputStream: InputStream): Drawable? {
        val svg =
            try {
                SVG.getFromInputStream(inputStream)
            } catch (_: Exception) {
                return null
            }

        val picture = svg.renderToPicture()
        return PictureDrawable(picture)
    }

    val iconPackFile = currentIconPack.value?.getIconFileForName(fileName, isDirectory, isExpanded)
    val icon = if (isDirectory) folder else getBuiltInFileIcon(fileName)

    val builtinIcon = icon.getDrawable(context)
    val iconPackIcon = iconPackFile?.inputStream()?.let { loadSvg(it) }

    return iconPackIcon ?: builtinIcon
}

private fun getBuiltInFileIcon(fileName: String): Int =
    when (fileName) {
        "contract.sol",
        "LICENSE",
        "NOTICE" -> text
        "gradlew",
        "gradlew.bat" -> gradle
        "README.md" -> info

        else -> {
            val ext = fileName.substringAfterLast('.', "")
            val type = FileType.fromExtension(ext)
            type.iconOverride?.get(ext) ?: type.icon ?: plain_file
        }
    }

private fun getBuiltInFileIcon(file: FileObject): Int = getBuiltInFileIcon(file.getName())
