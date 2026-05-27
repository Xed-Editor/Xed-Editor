package com.rk.filetree

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.rk.file.FileObject
import com.rk.file.FileTypeManager
import com.rk.icons.Icon
import com.rk.icons.XedIcon
import com.rk.icons.pack.currentIconPack
import com.rk.icons.rememberSvgImageLoader
import com.rk.resources.drawables
import com.rk.utils.loadSvg

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
            file.isDirectory() -> Icon.ResourceIcon(folder)
            file.isSymlink() -> Icon.ResourceIcon(fileSymlink)
            else -> Icon.ResourceIcon(unknown)
        }

    val tint =
        iconTint
            ?: if (icon is Icon.ResourceIcon && (icon.drawableRes == folder || icon.drawableRes == archive)) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.secondary

    val useTint = currentIconPack.value?.manifest?.applyTint == true

    if (iconPackFile != null) {
        AsyncImage(
            model = iconPackFile,
            imageLoader = imageLoader,
            contentDescription = null,
            colorFilter = if (useTint) ColorFilter.tint(tint) else null,
            modifier = Modifier.size(20.dp),
        )
    } else {
        XedIcon(icon = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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

    val icon = if (isDirectory) Icon.ResourceIcon(folder) else getBuiltInFileIcon(fileName)

    val tint =
        iconTint
            ?: if (icon is Icon.ResourceIcon && (icon.drawableRes == folder || icon.drawableRes == archive)) {
                MaterialTheme.colorScheme.primary
            } else MaterialTheme.colorScheme.secondary

    val useTint = currentIconPack.value?.manifest?.applyTint == true

    if (iconPackFile != null) {
        AsyncImage(
            model = iconPackFile,
            imageLoader = imageLoader,
            contentDescription = null,
            colorFilter = if (useTint) ColorFilter.tint(tint) else null,
            modifier = Modifier.size(20.dp),
        )
    } else {
        XedIcon(icon = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
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
fun getDrawableFileIcon(fileName: String, isDirectory: Boolean, isExpanded: Boolean = false): Drawable? {
    val iconPackFile = currentIconPack.value?.getIconFileForName(fileName, isDirectory, isExpanded)
    val icon = if (isDirectory) Icon.ResourceIcon(folder) else getBuiltInFileIcon(fileName)

    val builtinIcon = icon.toDrawable()
    val iconPackIcon = iconPackFile?.inputStream()?.let { loadSvg(it) }

    return iconPackIcon ?: builtinIcon
}

private fun getBuiltInFileIcon(fileName: String): Icon =
    when (fileName) {
        "contract.sol",
        "LICENSE",
        "NOTICE" -> Icon.ResourceIcon(text)
        "gradlew",
        "gradlew.bat" -> Icon.ResourceIcon(gradle)
        "README.md" -> Icon.ResourceIcon(info)

        else -> {
            val ext = fileName.substringAfterLast('.', "")
            val type = FileTypeManager.fromExtension(ext)
            type.iconOverride?.get(ext) ?: type.icon ?: Icon.ResourceIcon(plain_file)
        }
    }

private fun getBuiltInFileIcon(file: FileObject): Icon = getBuiltInFileIcon(file.getName())
