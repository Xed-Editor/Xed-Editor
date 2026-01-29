package com.rk.filetree

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
import com.rk.file.FileObject
import com.rk.file.FileType
import com.rk.icons.pack.currentIconPack
import com.rk.icons.rememberSvgImageLoader
import com.rk.resources.drawables
import com.rk.theme.folderSurface

private val plain_file = drawables.file
private val folder = drawables.folder
private val unknown = drawables.unknown_document
private val fileSymlink = drawables.file_symlink
private val archive = drawables.archive
private val text = drawables.text
private val gradle = drawables.gradle
private val info = drawables.info

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

private fun getBuiltInFileIcon(file: FileObject): Int =
    when (file.getName()) {
        "contract.sol",
        "LICENSE",
        "NOTICE" -> text
        "gradlew",
        "gradlew.bat" -> gradle
        "README.md" -> info

        else -> {
            val ext = file.getName().substringAfterLast('.', "")
            val type = FileType.fromExtension(ext)
            type.iconOverride?.get(ext) ?: type.icon ?: plain_file
        }
    }
